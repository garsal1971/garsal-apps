// ============================================================
// Job 2 — send-notifications
// Frequenza: ogni minuto (cron: "* * * * *")
//
// Legge cm_notification_queue (status='pending', fire_at <= now),
// invia la notifica sul canale configurato, aggiorna lo status,
// scrive il risultato in cm_notification_log.
//
// Max 50 notifiche per run per evitare timeout.
// ============================================================

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const SUPABASE_URL       = Deno.env.get('SUPABASE_URL')!
const SERVICE_ROLE_KEY   = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
const TELEGRAM_BOT_TOKEN = Deno.env.get('TELEGRAM_BOT_TOKEN')!

const sb = createClient(SUPABASE_URL, SERVICE_ROLE_KEY)

// ---------------------------------------------------------------------------
// Telegram
// ---------------------------------------------------------------------------
async function sendTelegram(
  chatId: string,
  text: string
): Promise<{ ok: boolean; response: string }> {
  const url = `https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage`
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ chat_id: chatId, text, parse_mode: 'HTML' }),
  })
  const body = await res.text()
  return { ok: res.ok, response: body }
}

// ---------------------------------------------------------------------------
// Handler principale
// ---------------------------------------------------------------------------
Deno.serve(async (_req) => {
  try {
    // Leggi notifiche da inviare
    const { data: items, error: queueError } = await sb
      .from('cm_notification_queue')
      .select('*')
      .eq('status', 'pending')
      .lte('fire_at', new Date().toISOString())
      .order('fire_at', { ascending: true })
      .limit(50)

    if (queueError) throw queueError

    let sent   = 0
    let failed = 0

    for (const item of items ?? []) {
      // Impostazioni notifica dell'utente
      const { data: settings } = await sb
        .from('cm_user_notification_settings')
        .select('telegram_chat_id, telegram_enabled')
        .eq('user_id', item.user_id)
        .single()

      let status: 'sent' | 'failed' = 'failed'
      let responseText = ''
      let errorMsg     = ''

      if (
        item.channel === 'telegram' &&
        settings?.telegram_enabled &&
        settings?.telegram_chat_id
      ) {
        const message = `${item.title}\n${item.body}`
        const result  = await sendTelegram(settings.telegram_chat_id, message)
        status        = result.ok ? 'sent' : 'failed'
        responseText  = result.response
        if (!result.ok) errorMsg = `Telegram API error: ${result.response}`
      } else {
        errorMsg = 'Canale non configurato o disabilitato'
      }

      // Aggiorna status nella queue
      await sb
        .from('cm_notification_queue')
        .update({ status })
        .eq('id', item.id)

      // Scrivi nel log storico
      await sb.from('cm_notification_log').insert({
        queue_id:  item.id,
        user_id:   item.user_id,
        app:       item.app,
        entity_id: item.entity_id,
        title:     item.title,
        channel:   item.channel,
        fired_at:  new Date().toISOString(),
        status,
        response:  responseText || null,
        error_msg: errorMsg     || null,
      })

      if (status === 'sent') sent++
      else failed++
    }

    const total = items?.length ?? 0
    console.log(`[send-notif] done — total:${total} sent:${sent} failed:${failed}`)

    return new Response(
      JSON.stringify({ ok: true, total, sent, failed }),
      { headers: { 'Content-Type': 'application/json' } }
    )
  } catch (err) {
    console.error('[send-notif] fatal:', err)
    return new Response(
      JSON.stringify({ ok: false, error: String(err) }),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
    )
  }
})
