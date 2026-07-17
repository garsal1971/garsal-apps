// Supabase Edge Function: suggerisce la categoria di spesa più adatta per un elenco di
// descrizioni di transazioni (Analisi Costi), usando Groq (free tier, modelli Llama).
// Nessun costo — richiede GROQ_API_KEY nei Supabase Secrets.
// v1 — 2026-07-17

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

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

  let body: { descriptions?: string[]; categories?: { id: string; name: string }[] };
  try {
    body = await req.json();
  } catch {
    return new Response(
      JSON.stringify({ error: { message: 'Body JSON non valido.' } }),
      { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }

  const descriptions = (body.descriptions || [])
    .filter((d): d is string => typeof d === 'string' && d.trim().length > 0)
    .slice(0, 200);
  const categories = body.categories || [];

  if (!descriptions.length || !categories.length) {
    return new Response(
      JSON.stringify({ error: { message: 'Servono "descriptions" e "categories" non vuoti.' } }),
      { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }

  const categoryList = categories.map((c) => `${c.id}: ${c.name}`).join('\n');
  const descList = descriptions.map((d, i) => `${i}: ${d}`).join('\n');

  const prompt = `Sei un assistente che categorizza transazioni bancarie personali (spese/entrate) in base alla descrizione/nome del merchant.

Categorie disponibili (id: nome):
${categoryList}

Descrizioni da classificare (indice: descrizione):
${descList}

Per ciascuna descrizione scegli l'id della categoria più adatta tra quelle elencate sopra. Se nessuna categoria è chiaramente adatta, usa null.
Rispondi SOLO con un oggetto JSON dove ogni chiave è l'indice (come stringa) e il valore è l'id categoria scelto (o null). Nessun testo aggiuntivo, nessuna spiegazione.`;

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
        response_format: { type: 'json_object' },
        temperature: 0,
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
    let parsed: Record<string, string | null> = {};
    try {
      parsed = JSON.parse(content);
    } catch {
      parsed = {};
    }

    const validIds = new Set(categories.map((c) => c.id));
    const results = descriptions.map((description, i) => {
      const catId = parsed[String(i)];
      return { description, categoryId: catId && validIds.has(catId) ? catId : null };
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
