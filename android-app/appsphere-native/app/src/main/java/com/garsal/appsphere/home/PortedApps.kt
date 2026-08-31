package com.garsal.appsphere.home

/** Destinazioni della navigazione. */
object Route {
    const val HOME = "home"
    const val SPUNTIAMOLA = "spuntiamola"
    const val OBIETTIVI = "obiettivi"
    const val EVENTS_LOG = "eventslog"
    const val TASKS = "tasks"
    const val TA_FIRI = "tafiri"
    const val PESO = "peso"
    const val MEMO = "memo"
    const val ABITUATI = "abituati"
    const val CALORIE = "calorie"
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

/**
 * Quali numeri sono punti.
 *
 * `cm_apps.score_query` restituisce un numero, ma non è sempre un punteggio:
 * per alcune app conta delle cose. Spuntiamola dà i giorni che mancano al
 * traguardo, Obiettivi gli obiettivi attivi, Memo le schede, le app del conto
 * familiare le transazioni in archivio. Quei numeri non si scrivono più sotto
 * il nome della bolla e non entrano nel totale che paga i premi: un conteggio
 * sommato ai punti è un saldo che nessuno può rifare a mano, e giorni che
 * mancano sommati a delle stelline non vogliono dire niente.
 *
 * ⚠️ **Lo stesso elenco vive in `index.html`** (`APP_SENZA_PUNTI`): se qui ne
 * aggiungi o togli una, riportalo là, o le due home mostreranno due totali
 * diversi — e un premio comprabile da una parte non lo sarebbe dall'altra.
 * L'elenco comprende anche app che in nativo non hanno una bolla: i loro punti
 * entrano comunque nel totale, quindi la domanda «sono punti?» le riguarda
 * esattamente come le altre.
 *
 * Il numero continua invece a **dimensionare** la bolla: quella di Spuntiamola
 * che si sgonfia man mano che i giorni finiscono è la cosa che rende utile
 * guardarla, e con l'area a zero sarebbe al minimo per sempre.
 */
object AppSenzaPunti {

    private val file = setOf(
        "spuntiamola.html",
        "calorie.html",
        "obiettivi.html",
        "memo.html",
        "finanza.html",
        "casarosa.html",
        "casaterrasini.html",
        "contabilita.html",
        "cost-analysis.html",
    )

    /** Vero se il numero di quell'app è un punteggio, e quindi si mostra e si somma. */
    fun contaComePunti(htmlFile: String?): Boolean = htmlFile !in file
}

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
        "ta-firi.html" to AppPortata(
            route = Route.TA_FIRI,
            titoloDiRipiego = "Ta Firi?",
            descrizioneDiRipiego = "Sfide a tempo — Ta Firi?",
            coloreDiRipiego = "#8E44AD",
        ),
        "habit-tracker.html" to AppPortata(
            route = Route.ABITUATI,
            titoloDiRipiego = "Abituati",
            descrizioneDiRipiego = "Abitudini a stack",
            coloreDiRipiego = "#1A1A1A",
        ),
        "memo.html" to AppPortata(
            route = Route.MEMO,
            titoloDiRipiego = "Memo",
            descrizioneDiRipiego = "Schede e appunti",
            coloreDiRipiego = "#2563EB",
        ),
        "calorie.html" to AppPortata(
            route = Route.CALORIE,
            titoloDiRipiego = "Calorie",
            descrizioneDiRipiego = "Diario alimentare",
            coloreDiRipiego = "#D97706",
        ),
        "weight-quest.html" to AppPortata(
            route = Route.PESO,
            titoloDiRipiego = "Ti pisasti?",
            descrizioneDiRipiego = "Il peso, giorno per giorno",
            coloreDiRipiego = "#00B894",
        ),
    )
}
