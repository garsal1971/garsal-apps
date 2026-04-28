package com.garsal.smartblocker

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

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

    /** Restituisce il device token, generandolo al primo accesso. */
    fun getDeviceToken(ctx: Context): String {
        val prefs = sp(ctx)
        var token = prefs.getString("device_token", null)
        if (token.isNullOrEmpty()) {
            token = UUID.randomUUID().toString()
            prefs.edit().putString("device_token", token).apply()
        }
        return token
    }
}
