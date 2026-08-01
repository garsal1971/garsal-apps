import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

const VERSION = "1.0.0";
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

// Attesa massima per l'aggiornamento prezzi preliminare. get-prices fa scraping
// su più fonti e può durare minuti: se sfora si prosegue comunque con i prezzi
// già in fnz_price_history, meglio uno snapshot con prezzi vecchi che nessuno.
const PRICES_TIMEOUT_MS = 5 * 60 * 1000;

type Row = Record<string, any>;

function log(level: string, message: string, data?: unknown) {
  console.log(JSON.stringify({ timestamp: new Date().toISOString(), level, message, ...(data === undefined ? {} : { data }) }));
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });
}

function num(value: unknown): number {
  const n = typeof value === "number" ? value : parseFloat(String(value));
  return Number.isFinite(n) ? n : 0;
}

// Data di oggi in Europa/Roma. Il job gira alle 23:00 locali: usare la data UTC
// darebbe il giorno giusto lo stesso, ma solo per coincidenza del fuso — meglio
// essere espliciti, così resta corretto anche se l'orario del cron cambia.
function romeToday(): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Europe/Rome", year: "numeric", month: "2-digit", day: "2-digit",
  }).format(new Date());
}

// ── Logica di calcolo — porting fedele di finanza.html ─────────────────────
// Le funzioni qui sotto replicano computePricesFromHistory / computeHoldings /
// portfolioStats / computeLoanValue / buildSnapshotPayload di finanza.html.
// Se una di quelle cambia, va cambiata anche qui: sono la stessa regola di
// business scritta due volte, una per il browser e una per il job notturno.

// Ultimo prezzo noto per simbolo, dedotto da fnz_price_history.
function pricesFromHistory(priceHistory: Row[]): Record<string, number> {
  const grouped: Record<string, Row[]> = {};
  for (const h of priceHistory) (grouped[h.symbol] ||= []).push(h);

  const prices: Record<string, number> = {};
  for (const [symbol, entries] of Object.entries(grouped)) {
    entries.sort((a, b) => String(a.id).localeCompare(String(b.id)));
    entries.sort((a, b) => String(a.price_date).localeCompare(String(b.price_date)));
    const last = entries[entries.length - 1];
    if (last) prices[symbol] = num(last.price);
  }
  return prices;
}

type Holding = {
  product: Row; qty: number; avgCost: number; costBasis: number;
  currentPrice: number | null; currentValue: number | null;
  pnl: number | null; pnlPct: number | null;
};

// Quantità residue e costo medio ponderato per prodotto di un portafoglio.
function computeHoldings(
  transactions: Row[], products: Map<string, Row>, prices: Record<string, number>, portfolioId: string,
): Holding[] {
  const txs = transactions
    .filter((t) => t.portfolio_id === portfolioId)
    .slice()
    .sort((a, b) => (a.date + a.created_at).localeCompare(b.date + b.created_at));

  const map = new Map<string, { qty: number; costBasis: number }>();
  for (const tx of txs) {
    if (!map.has(tx.product_id)) map.set(tx.product_id, { qty: 0, costBasis: 0 });
    const h = map.get(tx.product_id)!;
    const qty = num(tx.quantity);
    const prc = num(tx.price);
    const com = num(tx.commission);
    if (tx.type === "BUY") {
      h.costBasis += qty * prc + com;
      h.qty += qty;
    } else {
      if (h.qty > 0) h.costBasis -= (h.costBasis / h.qty) * qty;
      h.qty -= qty;
    }
  }

  const out: Holding[] = [];
  for (const [productId, h] of map) {
    if (h.qty <= 0.000001) continue;
    const product = products.get(productId);
    if (!product) continue;
    const avgCost = h.qty > 0 ? h.costBasis / h.qty : 0;
    const currentPrice = product.symbol in prices ? prices[product.symbol] : null;
    const currentValue = currentPrice != null ? h.qty * currentPrice : null;
    const pnl = currentValue != null ? currentValue - h.costBasis : null;
    const pnlPct = h.costBasis > 0 && pnl != null ? (pnl / h.costBasis) * 100 : null;
    out.push({ product, qty: h.qty, avgCost, costBasis: h.costBasis, currentPrice, currentValue, pnl, pnlPct });
  }
  return out;
}

type PortfolioStat = {
  portfolio: Row; holdings: Holding[]; totalValue: number; totalCost: number; pnl: number; pnlPct: number;
};

function portfolioStats(
  portfolios: Row[], transactions: Row[], products: Map<string, Row>, prices: Record<string, number>,
): PortfolioStat[] {
  return portfolios.map((p) => {
    const own = (num(p.ownership_percentage) || 100) / 100;
    const holdings = computeHoldings(transactions, products, prices, p.id);
    const totalValue = holdings.reduce((s, x) => s + (x.currentValue ?? x.costBasis), 0) * own;
    const totalCost = holdings.reduce((s, x) => s + x.costBasis, 0) * own;
    const pnl = totalValue - totalCost;
    return {
      portfolio: p, holdings, totalValue, totalCost, pnl,
      pnlPct: totalCost > 0 ? (pnl / totalCost) * 100 : 0,
    };
  });
}

// Debito residuo di un mutuo o prestito, quota di competenza inclusa.
function computeLoanValue(loan: Row, loanRepayments: Row[]): number {
  const today = new Date();
  const own = (num(loan.ownership_percentage) || 100) / 100;

  if (loan.type === "MUTUO") {
    const capitale = num(loan.capitale);
    const rata = num(loan.rata);
    const totaleRate = parseInt(String(loan.totale_rate), 10);
    const start = new Date(loan.giorno_prima_rata);

    let mesiPagati = (today.getFullYear() - start.getFullYear()) * 12 + (today.getMonth() - start.getMonth());
    mesiPagati = Math.max(0, Math.min(mesiPagati, totaleRate));

    // Tasso implicito della rata: Newton-Raphson sul valore attuale dell'annualità.
    let rate = 0.01;
    const tol = 1e-7;
    for (let i = 0; i < 1000; i++) {
      const f = rata * (1 - Math.pow(1 + rate, -totaleRate)) / rate - capitale;
      const df = (-rata * (1 - Math.pow(1 + rate, -totaleRate)) / (rate * rate)) +
                 (rata * totaleRate * Math.pow(1 + rate, -totaleRate - 1) / rate);
      const newRate = rate - f / df;
      if (Math.abs(newRate - rate) < tol) { rate = newRate; break; }
      rate = newRate;
    }

    if (Math.abs(rate) < 1e-8) return (capitale - rata * mesiPagati) * own;
    return (capitale * Math.pow(1 + rate, mesiPagati) - rata * (Math.pow(1 + rate, mesiPagati) - 1) / rate) * own;
  }

  // PRESTITO: capitale che matura interesse composto, ridotto dai rimborsi.
  const C = num(loan.capitale);
  const i = num(loan.interesse) / 100;
  const todayStr = today.toISOString().slice(0, 10);
  const repayments = loanRepayments
    .filter((r) => r.loan_id === loan.id && r.date <= todayStr)
    .slice()
    .sort((a, b) => String(a.date).localeCompare(String(b.date)) || String(a.created_at).localeCompare(String(b.created_at)));

  let balance = C;
  let cursorStr = loan.data_inizio_prestito;
  for (const r of repayments) {
    const days = Math.max(0, (+new Date(r.date) - +new Date(cursorStr)) / 86400000);
    balance = balance * Math.pow(1 + i, days / 365.25) - num(r.amount);
    cursorStr = r.date;
  }
  const daysToToday = Math.max(0, (+new Date(todayStr) - +new Date(cursorStr)) / 86400000);
  return balance * Math.pow(1 + i, daysToToday / 365.25) * own;
}

type SnapshotPayload = {
  topLevel: { patrimonio_netto: number; portafogli_totali: number; asset_totali: number; debiti_totali: number };
  details: { portfolios: Row[]; loans: Row[]; other_assets: Row[] };
};

function buildSnapshotPayload(
  portfolios: Row[], transactions: Row[], products: Map<string, Row>, prices: Record<string, number>,
  loans: Row[], loanRepayments: Row[], otherAssets: Row[],
): SnapshotPayload {
  const stats = portfolioStats(portfolios, transactions, products, prices);
  const grandValue = stats.reduce((s, x) => s + x.totalValue, 0);
  const totalLoans = loans.reduce((s, l) => s + (computeLoanValue(l, loanRepayments) || 0), 0);
  // danaro_rosa è escluso dai totali del patrimonio (come in finanza.html)
  const visibleAssets = otherAssets.filter((a) => a.asset_type !== "danaro_rosa");
  const totalOther = visibleAssets.reduce((s, a) => s + num(a.value) * (num(a.ownership_percentage) || 100) / 100, 0);

  const portfolioDetails = stats.map(({ portfolio: p, holdings, totalValue, totalCost, pnl, pnlPct }) => {
    const own = (num(p.ownership_percentage) || 100) / 100;
    return {
      id: p.id, name: p.name, color: p.color,
      ownership_pct: num(p.ownership_percentage) || 100,
      total_value: totalValue,
      total_value_full: own > 0 ? totalValue / own : totalValue,
      total_cost: totalCost, pnl, pnl_pct: pnlPct,
      holdings: holdings.map((hh) => ({
        symbol: hh.product.symbol, name: hh.product.name, asset_type: hh.product.asset_type,
        qty: hh.qty, avg_cost: hh.avgCost, cost_basis: hh.costBasis,
        current_price: hh.currentPrice, current_value: hh.currentValue,
        current_value_quota: hh.currentValue != null ? hh.currentValue * own : null,
        pnl: hh.pnl, pnl_pct: hh.pnlPct,
      })),
    };
  });

  const loanDetails = loans.map((l) => {
    const value = computeLoanValue(l, loanRepayments);
    const own = (num(l.ownership_percentage) || 100) / 100;
    return {
      id: l.id, type: l.type, name: l.name || (l.type === "MUTUO" ? "Mutuo" : "Prestito"),
      ownership_pct: num(l.ownership_percentage) || 100,
      residual_value: value,
      residual_value_full: own > 0 ? value / own : value,
    };
  });

  const assetDetails = visibleAssets.map((a) => {
    const own = (num(a.ownership_percentage) || 100) / 100;
    return {
      id: a.id, title: a.title, asset_type: a.asset_type,
      value: num(a.value),
      ownership_pct: num(a.ownership_percentage) || 100,
      ownership_value: num(a.value) * own,
      valuation_date: a.valuation_date,
    };
  });

  return {
    topLevel: {
      patrimonio_netto: grandValue + totalOther - totalLoans,
      portafogli_totali: grandValue,
      asset_totali: grandValue + totalOther,
      debiti_totali: totalLoans,
    },
    details: { portfolios: portfolioDetails, loans: loanDetails, other_assets: assetDetails },
  };
}

// ── Handler ────────────────────────────────────────────────────────────────

serve(async (req) => {
  const startTime = Date.now();
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  let body: Row = {};
  try { body = await req.json(); } catch { /* corpo vuoto: va bene */ }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRoleKey) {
    return json({ error: "Server configuration error: missing Supabase env vars" }, 500);
  }
  const supabase = createClient(supabaseUrl, serviceRoleKey);

  // ── 1. Aggiornamento prezzi preliminare ─────────────────────────────────
  let pricesResult: unknown = "skipped";
  if (body.skipPrices !== true) {
    try {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), PRICES_TIMEOUT_MS);
      const res = await fetch(`${supabaseUrl}/functions/v1/get-prices`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "Authorization": `Bearer ${serviceRoleKey}` },
        body: "{}",
        signal: controller.signal,
      });
      clearTimeout(timer);
      pricesResult = res.ok ? await res.json() : `HTTP ${res.status}`;
      log("INFO", "get-prices completato", { pricesResult });
    } catch (err) {
      // Timeout o errore di rete: si prosegue con i prezzi già in archivio.
      pricesResult = `error: ${err instanceof Error ? err.message : String(err)}`;
      log("WARN", "get-prices non completato, snapshot con i prezzi esistenti", { pricesResult });
    }
  }

  // Legge una tabella per intero, a pagine. PostgREST tronca le risposte oltre
  // un certo numero di righe: senza paginazione una tabella cresciuta abbastanza
  // (transazioni, storico prezzi) arriverebbe monca e lo snapshot risulterebbe
  // sbagliato senza che nulla segnali l'errore.
  const PAGE = 1000;
  const fetchAll = async (table: string, columns: string, orderBy: string): Promise<Row[]> => {
    const out: Row[] = [];
    for (let from = 0; ; from += PAGE) {
      const { data, error } = await supabase
        .from(table).select(columns).order(orderBy, { ascending: true }).range(from, from + PAGE - 1);
      if (error) throw new Error(`${table}: ${error.message}`);
      const page = (data ?? []) as Row[];
      out.push(...page);
      if (page.length < PAGE) return out;
    }
  };

  // ── 2. Caricamento dati (service role: vede tutti gli utenti) ───────────
  try {
    const [portfolios, transactions, products, priceHistory, loans, loanRepayments, otherAssets] = await Promise.all([
      fetchAll("fnz_portfolios", "id,user_id,name,color,ownership_percentage", "id"),
      fetchAll("fnz_transactions", "portfolio_id,product_id,type,quantity,price,commission,date,created_at", "id"),
      fetchAll("fnz_products", "id,symbol,name,asset_type", "id"),
      fetchAll("fnz_price_history", "id,symbol,price,price_date", "id"),
      fetchAll("fnz_loans", "*", "id"),
      fetchAll("fnz_loan_repayments", "loan_id,date,amount,created_at", "id"),
      fetchAll("fnz_other_assets", "id,user_id,title,asset_type,value,valuation_date,ownership_percentage", "id"),
    ]);
    log("INFO", "Dati caricati", {
      portfolios: portfolios.length, transactions: transactions.length, products: products.length,
      priceHistory: priceHistory.length, loans: loans.length, otherAssets: otherAssets.length,
    });

    const productMap = new Map<string, Row>(products.map((p: Row) => [p.id, p]));
    const prices = pricesFromHistory(priceHistory);

    // ── 3. Uno snapshot per ogni utente che ha dati di Finanza ─────────────
    const userIds = new Set<string>();
    for (const p of portfolios) userIds.add(p.user_id);
    for (const l of loans) userIds.add(l.user_id);
    for (const a of otherAssets) userIds.add(a.user_id);

    const snapshotDate = romeToday();
    const written: Row[] = [];

    for (const userId of userIds) {
      const userPortfolios = portfolios.filter((p: Row) => p.user_id === userId);
      const portfolioIds = new Set(userPortfolios.map((p: Row) => p.id));
      const userTransactions = transactions.filter((t: Row) => portfolioIds.has(t.portfolio_id));
      const userLoans = loans.filter((l: Row) => l.user_id === userId);
      const loanIds = new Set(userLoans.map((l: Row) => l.id));
      const userRepayments = loanRepayments.filter((r: Row) => loanIds.has(r.loan_id));
      const userAssets = otherAssets.filter((a: Row) => a.user_id === userId);

      const { topLevel, details } = buildSnapshotPayload(
        userPortfolios, userTransactions, productMap, prices, userLoans, userRepayments, userAssets,
      );

      const { error } = await supabase.from("fnz_dashboard_snapshots").upsert({
        user_id: userId,
        snapshot_date: snapshotDate,
        patrimonio_netto: topLevel.patrimonio_netto,
        portafogli_totali: topLevel.portafogli_totali,
        asset_totali: topLevel.asset_totali,
        debiti_totali: topLevel.debiti_totali,
        details,
        updated_at: new Date().toISOString(),
      }, { onConflict: "user_id,snapshot_date" });

      if (error) {
        log("ERROR", "Upsert snapshot fallito", { userId, message: error.message });
        written.push({ user_id: userId, ok: false, error: error.message });
        continue;
      }
      log("INFO", "Snapshot salvato", { userId, snapshotDate, ...topLevel });
      written.push({ user_id: userId, ok: true, ...topLevel });
    }

    return json({
      message: "OK", version: VERSION, snapshot_date: snapshotDate,
      users: written.length, written, prices: pricesResult,
      totalTimeMs: Date.now() - startTime,
    });
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err);
    log("ERROR", "save-snapshot fallita", { error: msg });
    return json({ error: msg }, 500);
  }
});
