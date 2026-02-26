// ============================================================
// Job 1 — fill-notification-queue
// Frequenza: ogni 6 ore (cron: "0 */6 * * *")
//
// Legge SOLO cm_notification_rules — nessuna query su altre tabelle.
// Le app sono responsabili di scrivere entity_title e due_at
// quando creano/modificano task, habit, ecc.
//
// Calcolo: fire_at = due_at - offset_minutes
// Inserisce in cm_notification_queue se:
//   - fire_at è nel futuro
//   - fire_at è entro HORIZON_DAYS giorni
// Usa UPSERT con ignoreDuplicates per idempotenza.
// ============================================================

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const SUPABASE_URL     = Deno.env.get('SUPABASE_URL')!
const SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

// Orizzonte massimo: inserisce solo notifiche entro 7 giorni
const HORIZON_DAYS = 7

const sb = createClient(SUPABASE_URL, SERVICE_ROLE_KEY)

// ---------------------------------------------------------------------------
// Tipi
// ---------------------------------------------------------------------------
interface Rule {
  id:             string
  user_id:        string
  app:            string
  entity_id:      string
  entity_title:   string
  due_at:         string     // ISO timestamptz — fornito dall'app
  offset_minutes: number
  offset_label:   string
  channel:        string
}

// ---------------------------------------------------------------------------
// Handler principale
// ---------------------------------------------------------------------------
Deno.serve(async (_req) => {
  try {
    const { data: rules, error: rulesError } = await sb
      .from('cm_notification_rules')
      .select('id, user_id, app, entity_id, entity_title, due_at, offset_minutes, offset_label, channel')
      .eq('enabled', true)

    if (rulesError) throw rulesError

    const now     = new Date()
    const horizon = new Date(now.getTime() + HORIZON_DAYS * 24 * 60 * 60 * 1000)

    let inserted = 0
    let skipped  = 0
    let errors   = 0

    for (const rule of (rules as Rule[]) ?? []) {
      const dueAt  = new Date(rule.due_at)
      const fireAt = new Date(dueAt.getTime() - rule.offset_minutes * 60 * 1000)

      // Salta se già passato o oltre l'orizzonte
      if (fireAt <= now || fireAt > horizon) { skipped++; continue }

      const { error: upsertError } = await sb
        .from('cm_notification_queue')
        .upsert(
          {
            rule_id:   rule.id,
            user_id:   rule.user_id,
            app:       rule.app,
            entity_id: rule.entity_id,
            title:     `🔔 ${rule.entity_title}`,
            body:      `Promemoria: ${rule.offset_label} prima`,
            channel:   rule.channel,
            fire_at:   fireAt.toISOString(),
            status:    'pending',
          },
          { onConflict: 'rule_id,fire_at', ignoreDuplicates: true }
        )

      if (upsertError) {
        console.error(`[fill-queue] upsert error rule=${rule.id}:`, upsertError)
        errors++
      } else {
        inserted++
      }
    }

    const total = (rules as Rule[])?.length ?? 0
    console.log(`[fill-queue] done — rules:${total} inserted:${inserted} skipped:${skipped} errors:${errors}`)

    return new Response(
      JSON.stringify({ ok: true, total, inserted, skipped, errors }),
      { headers: { 'Content-Type': 'application/json' } }
    )
  } catch (err) {
    console.error('[fill-queue] fatal:', err)
    return new Response(
      JSON.stringify({ ok: false, error: String(err) }),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
    )
  }
})
