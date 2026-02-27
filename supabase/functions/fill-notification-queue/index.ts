// ============================================================
// Job 1 — fill-notification-queue
// Frequenza: ogni 6 ore (cron: "0 */6 * * *")
//
// Legge cm_notification_rules (enabled=true).
// reminder_presets JSONB: { "reminders": [1, 3, 5], "due_at": "2026-02-26T10:00:00" }
//
// Per ogni regola:
//   - estrae due_at e array int_id preset da reminder_presets
//   - risolve offset_minutes da cm_reminder_presets (mappa caricata una sola volta)
//   - calcola fire_at = due_at − offset_minutes
//   - elimina entry pending stale  (fire_at > now+SAFE_WINDOW, escluse le imminenti)
//   - inserisce le nuove entry entro HORIZON_DAYS
//
// Idempotente: UPSERT con ignoreDuplicates=true su conflict (rule_id, fire_at)
// ============================================================

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const SUPABASE_URL     = Deno.env.get('SUPABASE_URL')!
const SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

// Orizzonte massimo: inserisce solo notifiche entro 7 giorni
const HORIZON_DAYS   = 7
// Finestra di sicurezza: non toccare entry che sparano entro 2 minuti
const SAFE_WINDOW_MS = 2 * 60 * 1000

const sb = createClient(SUPABASE_URL, SERVICE_ROLE_KEY)

// ---------------------------------------------------------------------------
// Tipi
// ---------------------------------------------------------------------------
interface ReminderPresetsJson {
  reminders: number[]   // array di int_id da cm_reminder_presets
  due_at:    string     // ISO timestamptz — data/ora di scadenza del task
}

interface Rule {
  id:               string
  user_id:          string
  app:              string
  entity_id:        string
  entity_title:     string | null
  reminder_presets: ReminderPresetsJson
  channel:          string
}

interface Preset {
  int_id:         number
  label:          string
  offset_minutes: number
}

// ---------------------------------------------------------------------------
// Handler principale
// ---------------------------------------------------------------------------
Deno.serve(async (_req) => {
  try {
    const now        = new Date()
    const horizon    = new Date(now.getTime() + HORIZON_DAYS * 24 * 60 * 60 * 1000)
    const safeDelete = new Date(now.getTime() + SAFE_WINDOW_MS)

    // 1. Carica tutti i preset una sola volta → mappa int_id → {label, offset_minutes}
    const { data: presetsRows, error: presetsError } = await sb
      .from('cm_reminder_presets')
      .select('int_id, label, offset_minutes')

    if (presetsError) throw presetsError

    const presetsMap = new Map<number, { label: string; offset_minutes: number }>()
    for (const p of (presetsRows as Preset[]) ?? []) {
      presetsMap.set(p.int_id, { label: p.label, offset_minutes: p.offset_minutes })
    }
    console.log(`[fill-queue] preset caricati: ${presetsMap.size}`)

    // 2. Carica tutte le regole attive
    const { data: rules, error: rulesError } = await sb
      .from('cm_notification_rules')
      .select('id, user_id, app, entity_id, entity_title, reminder_presets, channel')
      .eq('enabled', true)

    if (rulesError) throw rulesError

    let inserted = 0
    let skipped  = 0
    let deleted  = 0
    let errors   = 0

    for (const rule of (rules as Rule[]) ?? []) {
      const rp = rule.reminder_presets

      // Salta regole senza dati validi
      if (!rp?.due_at || !rp?.reminders?.length) {
        console.warn(`[fill-queue] regola ${rule.id} senza due_at o reminders, skip`)
        skipped++
        continue
      }

      const dueAt = new Date(rp.due_at)

      // 3. Calcola fire_at per ogni preset selezionato nella regola
      const validEntries: Array<{ fire_at: string; label: string }> = []

      for (const pid of rp.reminders) {
        const preset = presetsMap.get(pid)
        if (!preset) {
          console.warn(`[fill-queue] preset int_id=${pid} non trovato, skip`)
          continue
        }
        const fireAt = new Date(dueAt.getTime() - preset.offset_minutes * 60 * 1000)
        if (fireAt > now && fireAt <= horizon) {
          validEntries.push({ fire_at: fireAt.toISOString(), label: preset.label })
        }
      }

      // 4. Elimina entry pending stale per questa regola
      //    (solo quelle con fire_at oltre la finestra di sicurezza)
      const { error: delErr, count: delCount } = await sb
        .from('cm_notification_queue')
        .delete({ count: 'exact' })
        .eq('rule_id', rule.id)
        .eq('status', 'pending')
        .gt('fire_at', safeDelete.toISOString())

      if (delErr) {
        console.error(`[fill-queue] delete error rule=${rule.id}:`, delErr)
        errors++
      } else {
        deleted += delCount ?? 0
      }

      // 5. Inserisce le nuove entry (UPSERT idempotente)
      const title = rule.entity_title ? `🔔 ${rule.entity_title}` : `🔔 Promemoria`

      for (const entry of validEntries) {
        const body = `${entry.label} prima — scad. ${formatDate(dueAt)}`

        const { error: upsertError } = await sb
          .from('cm_notification_queue')
          .upsert(
            {
              rule_id:   rule.id,
              user_id:   rule.user_id,
              app:       rule.app,
              entity_id: rule.entity_id,
              title,
              body,
              channel:   rule.channel,
              fire_at:   entry.fire_at,
              status:    'pending',
            },
            { onConflict: 'rule_id,fire_at', ignoreDuplicates: true }
          )

        if (upsertError) {
          console.error(`[fill-queue] upsert error rule=${rule.id} fire_at=${entry.fire_at}:`, upsertError)
          errors++
        } else {
          inserted++
        }
      }

      if (validEntries.length === 0) skipped++
    }

    const total = (rules as Rule[])?.length ?? 0
    console.log(
      `[fill-queue] done — rules:${total} inserted:${inserted} deleted:${deleted} skipped:${skipped} errors:${errors}`
    )

    return new Response(
      JSON.stringify({ ok: true, total, inserted, deleted, skipped, errors }),
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

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Formatta una Date come "dd/mm/yyyy HH:MM" (ora locale del server) */
function formatDate(d: Date): string {
  const dd   = String(d.getUTCDate()).padStart(2, '0')
  const mm   = String(d.getUTCMonth() + 1).padStart(2, '0')
  const yyyy = d.getUTCFullYear()
  const hh   = String(d.getUTCHours()).padStart(2, '0')
  const min  = String(d.getUTCMinutes()).padStart(2, '0')
  return `${dd}/${mm}/${yyyy} ${hh}:${min}`
}
