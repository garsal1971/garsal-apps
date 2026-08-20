package com.garsal.appsphere.peso

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Il ponte con Health Connect e con la bilancia Renpho — `HealthConnectBridge.kt`
 * e `openRenpho()` di `weight-quest.html`, qui senza passare da un
 * `JavascriptInterface`: l'app nativa chiama l'SDK direttamente.
 *
 * ⚠️ La finestra di lettura (90 giorni, +25 ore di margine) e il fatto che sia
 * la sola lettura del peso a servire sono le stesse del web — cambiando una
 * delle due cose va cambiata anche l'altra.
 */

/** Il pacchetto della bilancia, aperta da qui come `openRenpho()`. */
private const val PACCHETTO_RENPHO = "com.renpho.health"

/** L'unico permesso che serve: leggere il peso, mai scriverlo. */
internal val PERMESSI_SALUTE = setOf(HealthPermission.getReadPermission(WeightRecord::class))

/** Una pesata letta da Health Connect: istante e peso in kg. */
data class PuntoSalute(val timestamp: Long, val pesoKg: Double)

/**
 * La data locale della pesata — la stessa che finisce nella riga scritta
 * ([com.garsal.appsphere.peso.PesoRepository.rigaPuntoSalute]) e quella con
 * cui si riconoscono le pesate manuali diventate ridondanti: un solo posto
 * dove questo calcolo vive, invece di due che potrebbero disallinearsi.
 */
fun PuntoSalute.giorno(): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toString()

sealed class EsitoSalute {
    data class Ok(val punti: List<PuntoSalute>) : EsitoSalute()
    data class Errore(val messaggio: String) : EsitoSalute()
    /** Il permesso di lettura non è (ancora) concesso: il chiamante deve chiederlo e ritentare. */
    object PermessiRichiesti : EsitoSalute()
}

object SaluteRepository {

    fun sdkDisponibile(context: Context): Boolean =
        runCatching { HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE }
            .getOrDefault(false)

    /** Apre Renpho, o lo Store se non è installata — `openRenpho()` nel web. */
    fun apriRenpho(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(PACCHETTO_RENPHO)
        if (intent != null) {
            context.startActivity(intent)
        } else {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$PACCHETTO_RENPHO"),
                )
            )
        }
    }

    /**
     * Le pesate degli ultimi 90 giorni — `doSync()` nel web. Il margine di 25
     * ore sul limite superiore è lo stesso: copre pesate di oggi scritte con
     * un orologio del telefono leggermente avanti.
     */
    suspend fun leggiPeso(context: Context): EsitoSalute = withContext(Dispatchers.IO) {
        if (!sdkDisponibile(context)) {
            return@withContext EsitoSalute.Errore(
                "Health Connect non è installato. Installalo dal Play Store e riprova."
            )
        }
        try {
            val client = HealthConnectClient.getOrCreate(context)
            val fine = Instant.now().plus(25, ChronoUnit.HOURS)
            val inizio = Instant.now().minus(90, ChronoUnit.DAYS)
            val risposta = client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter(startTime = inizio, endTime = fine),
                )
            )
            EsitoSalute.Ok(risposta.records.map { PuntoSalute(it.time.toEpochMilli(), it.weight.inKilograms) })
        } catch (e: SecurityException) {
            EsitoSalute.PermessiRichiesti
        } catch (e: Exception) {
            EsitoSalute.Errore(e.message ?: "Errore Health Connect")
        }
    }
}
