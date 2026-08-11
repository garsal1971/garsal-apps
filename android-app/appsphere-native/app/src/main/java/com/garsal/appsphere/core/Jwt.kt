package com.garsal.appsphere.core

import android.util.Base64
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Lettura dei claim di un JWT, senza verificarne la firma.
 *
 * Serve a una cosa sola: sapere chi è l'utente (`sub`, cioè lo stesso valore
 * che le RLS leggono come `auth.uid()`) anche quando la chiamata a `/user` non
 * ha risposto. Il token arriva dal server nel fragment del deep link e viene
 * comunque riverificato da PostgREST a ogni richiesta: qui non si sta decidendo
 * niente sulla base della firma, si sta solo leggendo un'etichetta.
 */
object Jwt {

    private val json = Json { ignoreUnknownKeys = true }

    fun claim(token: String, nome: String): String? = try {
        val payload = token.split(".").getOrNull(1)
        payload?.let {
            val grezzo = Base64.decode(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            json.parseToJsonElement(String(grezzo, Charsets.UTF_8))
                .jsonObject[nome]
                ?.jsonPrimitive
                ?.content
        }
    } catch (e: Exception) {
        Log.w("AppSphereAuth", "claim '$nome' non leggibile dal JWT: ${e.message}")
        null
    }
}
