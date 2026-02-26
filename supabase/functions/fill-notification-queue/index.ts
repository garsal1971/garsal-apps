// ============================================================
// Job 1 — fill-notification-queue
// Frequenza: ogni 6 ore (cron: "0 */6 * * *")
//
// Legge cm_notification_rules (enabled=true), calcola fire_at
// per ogni entità e inserisce in cm_notification_queue.
// Usa UPSERT con ignoreDuplicates per idempotenza.
//
// App supportate:
//   tasks  → ts_tasks (title, type, next_occurrence_date, start_date)
//   habits → hb_habits (name) — fire daily at DUE_HOUR_UTC
// ============================================================

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const SUPABASE_URL       = Deno.env.get('SUPABASE_URL')!
const SERVICE_ROLE_KEY   = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

// Ora di scadenza assunta per task senza ora esplicita (09:00 UTC)
const DUE_HOUR_UTC = 9
// Orizzonte massimo: inserisce solo notifiche entro 7 giorni
const HORIZON_DAYS = 7

const sb = createClient(SUPABASE_URL, SERVICE_ROLE_KEY)

// ---------------------------------------------------------------------------
// Tipi
// ---------------------------------------------------------------------------
interface Rule {
  id: string
  user_id: string
  app: string
  entity_id: string
  entity_type: string
  offset_minutes: number
  offset_label: string
  channel: string
}

interface EntityInfo {
  title: string
  dueDate: Date
}

// ---------------------------------------------------------------------------
// Lookup task
// ---------------------------------------------------------------------------
async function getTaskInfo(entityId: string): Promise<EntityInfo | null> {
  const { data, error } = await sb
    .from('ts_tasks')
    .select('title, type, start_date, next_occurrence_date, status')
    .eq('id', entityId)
    .single()

  if (error || !data) return null
  if (data.status !== 'active') return null

  // Scegli la data di riferimento in base al tipo
  let dateStr: string | null = null
  switch (data.type) {
    case 'single':
    case 'recurring':
    case 'simple_recurring':
    case 'workflow':
      dateStr = data.next_occurrence_date || data.start_date
      break
    default:
      // free_repeat, multiple: nessuna data fissa — skip
      return null
  }

  if (!dateStr) return null

  let dueDate: Date
  if (dateStr.includes('T')) {
    // ISO datetime completo
    dueDate = new Date(dateStr)
  } else {
    // YYYY-MM-DD → imposta a DUE_HOUR_UTC
    const [y, m, d] = dateStr.split('-').map(Number)
    dueDate = new Date(Date.UTC(y, m - 1, d, DUE_HOUR_UTC, 0, 0))
  }

  return { title: data.title, dueDate }
}

// ---------------------------------------------------------------------------
// Lookup habit
// ---------------------------------------------------------------------------
async function getHabitInfo(entityId: string): Promise<EntityInfo | null> {
  const { data, error } = await sb
    .from('hb_habits')
    .select('name')
    .eq('id', entityId)
    .single()

  if (error || !data) return null

  // Habit = ricorrenza giornaliera: prossimo trigger = oggi alle DUE_HOUR_UTC
  const now = new Date()
  const dueDate = new Date(
    Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate(), DUE_HOUR_UTC, 0, 0)
  )
  // Se l'ora di oggi è già passata, usa domani
  if (dueDate <= now) dueDate.setUTCDate(dueDate.getUTCDate() + 1)

  return { title: data.name, dueDate }
}

// ---------------------------------------------------------------------------
// Handler principale
// ---------------------------------------------------------------------------
Deno.serve(async (_req) => {
  try {
    const { data: rules, error: rulesError } = await sb
      .from('cm_notification_rules')
      .select('*')
      .eq('enabled', true)

    if (rulesError) throw rulesError

    const now     = new Date()
    const horizon = new Date(now.getTime() + HORIZON_DAYS * 24 * 60 * 60 * 1000)

    let inserted = 0
    let skipped  = 0
    let errors   = 0

    for (const rule of (rules as Rule[]) ?? []) {
      // Recupera info entità
      let info: EntityInfo | null = null
      if      (rule.app === 'tasks')  info = await getTaskInfo(rule.entity_id)
      else if (rule.app === 'habits') info = await getHabitInfo(rule.entity_id)

      if (!info) { skipped++; continue }

      // Calcola fire_at
      const fireAt = new Date(info.dueDate.getTime() - rule.offset_minutes * 60 * 1000)

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
            title:     `🔔 ${info.title}`,
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
