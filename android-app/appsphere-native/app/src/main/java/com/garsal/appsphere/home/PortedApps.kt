package com.garsal.appsphere.home

/** Destinazioni della navigazione. */
object Route {
    const val HOME = "home"
    const val SPUNTIAMOLA = "spuntiamola"
    const val OBIETTIVI = "obiettivi"
    const val EVENTS_LOG = "eventslog"
}

/**
 * Cosa compare in home.
 *
 * Il web mostra tutte le righe attive di `cm_apps`; qui si mostrano solo le app
 * che esistono davvero in nativo. Le altre non sono nascoste per pudore: non
 * esistono proprio in questo APK, e per usarle si apre quello WebView, che
 * resta installato accanto.
 *
 * Titolo, descrizione, colore e punteggio continuano ad arrivare dal database:
 * questo registro decide soltanto *se* la bolla si disegna e *dove* porta il
 * tap. Portare una quarta app in nativo = una riga qui più le sue schermate.
 *
 * I valori di ripiego servono perché non tutte le righe di `cm_apps` sono nate
 * da una migration — la tabella fu popolata a mano prima che esistesse la
 * cartella `migrations/`, e per esempio `events-log.html` non compare in
 * nessun file SQL della repo. Se la riga manca, la bolla si disegna lo stesso
 * invece di sparire senza che nessuno se ne accorga.
 */
data class AppPortata(
    val route: String,
    val titoloDiRipiego: String,
    val descrizioneDiRipiego: String,
    val coloreDiRipiego: String,
)

object PortedApps {

    val perHtmlFile: Map<String, AppPortata> = mapOf(
        "spuntiamola.html" to AppPortata(
            route = Route.SPUNTIAMOLA,
            titoloDiRipiego = "Spuntiamola",
            descrizioneDiRipiego = "Conto alla rovescia a spunte",
            coloreDiRipiego = "#7C3AED",
        ),
        "obiettivi.html" to AppPortata(
            route = Route.OBIETTIVI,
            titoloDiRipiego = "Obiettivi",
            descrizioneDiRipiego = "Obiettivi annuali e trimestrali",
            coloreDiRipiego = "#0891B2",
        ),
        "events-log.html" to AppPortata(
            route = Route.EVENTS_LOG,
            titoloDiRipiego = "Events Log",
            descrizioneDiRipiego = "Registro di eventi e attività",
            coloreDiRipiego = "#00A651",
        ),
    )
}
