package com.garsal.appsphere.home

/** Destinazioni della navigazione. */
object Route {
    const val HOME = "home"
    const val SPUNTIAMOLA = "spuntiamola"
    const val OBIETTIVI = "obiettivi"
    const val EVENTS_LOG = "eventslog"
    const val TASKS = "tasks"
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
        // ⚠️ Obiettivi è **sospesa in home, non rimossa dal progetto**: le sue
        // schermate (`obiettivi/`) e la rotta `Route.OBIETTIVI` restano dove
        // sono, compilate e funzionanti, e per rimetterla in home basta
        // riattivare questa riga. Sul web e nell'APK WebView non cambia niente:
        // `cm_apps` non è stata toccata, quindi la bolla di Obiettivi è ancora
        // lì e apre `obiettivi.html` come sempre.
        //
        //  "obiettivi.html" to AppPortata(
        //      route = Route.OBIETTIVI,
        //      titoloDiRipiego = "Obiettivi",
        //      descrizioneDiRipiego = "Obiettivi annuali e trimestrali",
        //      coloreDiRipiego = "#0891B2",
        //  ),
        "tasks.html" to AppPortata(
            route = Route.TASKS,
            titoloDiRipiego = "Tasks",
            descrizioneDiRipiego = "Task e ricorrenze",
            coloreDiRipiego = "#FF3366",
        ),
        "events-log.html" to AppPortata(
            route = Route.EVENTS_LOG,
            titoloDiRipiego = "Events Log",
            descrizioneDiRipiego = "Registro di eventi e attività",
            coloreDiRipiego = "#00A651",
        ),
    )
}
