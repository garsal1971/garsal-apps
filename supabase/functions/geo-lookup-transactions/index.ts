// Supabase Edge Function: categorizza le transazioni (Analisi Costi) cercando prima ogni
// merchant su OpenStreetMap Nominatim (gratis, nessuna chiave richiesta) aggiungendo la città
// indicata, e passando il tipo di locale trovato (es. farmacia, ristorante, supermercato) come
// indizio a Groq insieme alla descrizione, per un'unica classificazione finale.
//
// Nominatim usage policy: max 1 richiesta/secondo e User-Agent identificativo obbligatori —
// per questo il batch è tenuto piccolo (max 6 descrizioni a chiamata).
//
// v1 — 2026-07-17: prima versione, usata come stadio di fallback dopo un primo passaggio AI
// testuale (categorize-transactions).
// v2 — 2026-07-18: diventa lo stadio principale — la ricerca geografica parte sempre per prima
// su ogni merchant, poi Groq classifica usando anche l'eventuale indizio trovato. Aggiunto il
// supporto alle regole già definite dall'utente (come in categorize-transactions), e il campo
// "source" per risultato distingue se è stato usato un indizio geografico ('geo') o solo il
// testo della descrizione ('text').

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

const NOMINATIM_USER_AGENT = 'AnalisiCosti/1.0 (Supabase Edge Function; contatto: garsal1971@gmail.com)';

function extractJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    const start = text.indexOf('{');
    const end = text.lastIndexOf('}');
    if (start !== -1 && end !== -1 && end > start) {
      try {
        return JSON.parse(text.slice(start, end + 1));
      } catch {
        return null;
      }
    }
    return null;
  }
}

async function nominatimLookup(query: string): Promise<string | null> {
  try {
    const url = `https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q=${encodeURIComponent(query)}`;
    const res = await fetch(url, { headers: { 'User-Agent': NOMINATIM_USER_AGENT } });
    if (!res.ok) return null;
    const data = await res.json();
    const place = Array.isArray(data) ? data[0] : null;
    if (!place) return null;
    const cls = (place.class || '').replace(/_/g, ' ');
    const type = (place.type || '').replace(/_/g, ' ');
    const name = place.name || '';
    if (!cls && !type) return null;
    return `${cls}/${type}${name ? ' — ' + name : ''}`;
  } catch {
    return null;
  }
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { status: 204, headers: corsHeaders });
  }
  if (req.method !== 'POST') {
    return new Response('Method Not Allowed', { status: 405, headers: corsHeaders });
  }

  const apiKey = Deno.env.get('GROQ_API_KEY');
  if (!apiKey) {
    return new Response(
      JSON.stringify({ error: { message: 'GROQ_API_KEY non configurata nei Supabase Secrets.' } }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }

  let body: {
    descriptions?: string[];
    categories?: { id: string; name: string }[];
    rules?: { pattern: string; categoryName: string }[];
    city?: string;
  };
  try {
    body = await req.json();
  } catch {
    return new Response(
      JSON.stringify({ error: { message: 'Body JSON non valido.' } }),
      { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }

  // Batch piccolo: rispettiamo il limite di 1 richiesta/secondo di Nominatim e restiamo
  // dentro al timeout della edge function (10s sul piano free di Supabase).
  const descriptions = (body.descriptions || [])
    .filter((d): d is string => typeof d === 'string' && d.trim().length > 0)
    .slice(0, 6);
  const categories = body.categories || [];
  const rules = (body.rules || [])
    .filter((r) => r && typeof r.pattern === 'string' && typeof r.categoryName === 'string')
    .slice(0, 150);
  const city = (body.city || '').trim();

  if (!descriptions.length || !categories.length) {
    return new Response(
      JSON.stringify({ error: { message: 'Servono "descriptions" e "categories" non vuoti.' } }),
      { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }

  // Ricerca geografica in sequenza, con pausa per rispettare il limite di 1 richiesta/secondo.
  const hints: (string | null)[] = [];
  for (let i = 0; i < descriptions.length; i++) {
    const query = city ? `${descriptions[i]} ${city}` : descriptions[i];
    hints.push(await nominatimLookup(query));
    if (i < descriptions.length - 1) await new Promise((r) => setTimeout(r, 1100));
  }

  const categoryList = categories.map((c, i) => `${i}: ${c.name}`).join('\n');
  const descList = descriptions
    .map((d, i) => `${i}: ${d}${hints[i] ? ` (indizio geografico: ${hints[i]})` : ''}`)
    .join('\n');
  const rulesSection = rules.length
    ? `\nRegole già definite manualmente dall'utente (usale come riferimento per capire il suo stile di categorizzazione, non sono vincolanti):\n${rules
        .map((r) => `"${r.pattern}" → ${r.categoryName}`)
        .join('\n')}\n`
    : '';

  const prompt = `Sei un assistente che categorizza transazioni bancarie personali (spese/entrate) in base alla descrizione del merchant. Per alcune descrizioni è disponibile anche un indizio da una ricerca geografica (tipo di locale trovato su OpenStreetMap, in inglese: es. amenity/pharmacy, shop/supermarket).

Categorie disponibili (indice: nome):
${categoryList}
${rulesSection}
Descrizioni da classificare (indice: descrizione, con eventuale indizio geografico):
${descList}

Per ciascuna descrizione scegli l'INDICE della categoria più adatta, usando anche l'indizio geografico quando presente. Se resta incerto, usa null.
Rispondi SOLO con un oggetto JSON compatto, senza markdown né spiegazioni, con questa forma esatta:
{"0": 2, "1": null}
dove ogni chiave è l'indice della descrizione e il valore è l'indice della categoria scelta (o null).`;

  try {
    const groqRes = await fetch('https://api.groq.com/openai/v1/chat/completions', {
      method: 'POST',
      headers: {
        Authorization: 'Bearer ' + apiKey,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        model: 'llama-3.3-70b-versatile',
        messages: [{ role: 'user', content: prompt }],
        temperature: 0,
        max_tokens: 512,
      }),
    });

    const data = await groqRes.json();
    if (!groqRes.ok) {
      return new Response(
        JSON.stringify({ error: { message: 'Errore Groq: ' + (data?.error?.message || groqRes.status) } }),
        { status: 502, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const content: string = data?.choices?.[0]?.message?.content || '{}';
    const parsed = (extractJson(content) as Record<string, number | string | null> | null) || {};

    const results = descriptions.map((description, i) => {
      const raw = parsed[String(i)];
      const catIdx = typeof raw === 'number' ? raw : typeof raw === 'string' ? parseInt(raw, 10) : NaN;
      const cat = Number.isInteger(catIdx) && catIdx >= 0 && catIdx < categories.length ? categories[catIdx] : null;
      return { description, categoryId: cat ? cat.id : null, source: hints[i] ? 'geo' : 'text' };
    });

    return new Response(JSON.stringify({ results }), {
      status: 200,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  } catch (e) {
    return new Response(
      JSON.stringify({ error: { message: 'Errore chiamata Groq: ' + (e as Error).message } }),
      { status: 502, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
