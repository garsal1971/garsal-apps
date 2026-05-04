package com.garsal.smartblocker

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "smartblocker_prefs"

    const val STATE_NONE      = 0  // nessun blocco
    const val STATE_TRIGGERED = 1  // bloccato, rinvio disponibile
    const val STATE_LOCKED    = 2  // bloccato, solo PIN

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getState(ctx: Context): Int        = sp(ctx).getInt("block_state", STATE_NONE)
    fun setState(ctx: Context, s: Int)     { sp(ctx).edit().putInt("block_state", s).apply() }

    fun getSnoozeCount(ctx: Context): Int  = sp(ctx).getInt("snooze_count", 0)
    fun setSnoozeCount(ctx: Context, n: Int) { sp(ctx).edit().putInt("snooze_count", n).apply() }

    fun getSnoozeUntil(ctx: Context): Long = sp(ctx).getLong("snooze_until", 0L)
    fun setSnoozeUntil(ctx: Context, ms: Long) { sp(ctx).edit().putLong("snooze_until", ms).apply() }

    fun getLastTrigger(ctx: Context): String = sp(ctx).getString("last_trigger", "") ?: ""
    fun setLastTrigger(ctx: Context, k: String) { sp(ctx).edit().putString("last_trigger", k).apply() }

    /** Entity IDs dei task da completare quando l'utente sblocca con PIN (separati da virgola). */
    fun getBlockEntityIds(ctx: Context): List<String> {
        val raw = sp(ctx).getString("block_entity_ids", "") ?: ""
        return raw.split(",").filter { it.isNotBlank() }
    }
    fun setBlockEntityIds(ctx: Context, ids: List<String>) {
        sp(ctx).edit().putString("block_entity_ids", ids.joinToString(",")).apply()
    }
    fun clearBlockEntityIds(ctx: Context) { sp(ctx).edit().remove("block_entity_ids").apply() }

    /** Titolo del blocco attivo (join dei titoli dei task pendenti). */
    fun getBlockTitle(ctx: Context): String = sp(ctx).getString("block_title", "") ?: ""
    fun setBlockTitle(ctx: Context, title: String) { sp(ctx).edit().putString("block_title", title).apply() }
    fun clearBlockTitle(ctx: Context) { sp(ctx).edit().remove("block_title").apply() }

    /** Data del blocco (fireAt ISO, es. "2026-05-04") — usata come p_today in task_complete. */
    fun getBlockDate(ctx: Context): String = sp(ctx).getString("block_date", "") ?: ""
    fun setBlockDate(ctx: Context, date: String) { sp(ctx).edit().putString("block_date", date).apply() }
    fun clearBlockDate(ctx: Context) { sp(ctx).edit().remove("block_date").apply() }

    /** Token dispositivo — impostato da Supabase via get_smart_block_token() RPC. */
    fun getDeviceToken(ctx: Context): String = sp(ctx).getString("device_token", "") ?: ""
    fun setDeviceToken(ctx: Context, token: String) { sp(ctx).edit().putString("device_token", token).apply() }

    /** Avvia il servizio automaticamente al boot del dispositivo. Default: true. */
    fun getAutoStart(ctx: Context): Boolean = sp(ctx).getBoolean("auto_start", true)
    fun setAutoStart(ctx: Context, v: Boolean) { sp(ctx).edit().putBoolean("auto_start", v).apply() }
}
