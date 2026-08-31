package com.garsal.appsphere.calorie

import com.garsal.appsphere.peso.Obiettivo
import com.garsal.appsphere.peso.Pesata
import com.garsal.appsphere.peso.PesoRegole
import com.garsal.appsphere.peso.Traguardo
import java.time.LocalDate
import java.time.Period
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Il conto delle calorie, ricalcato da `calorie.html` funzione per funzione.
 *
 * ⚠️ **Queste regole vivono in due implementazioni** — questa e la pagina — e
 * vanno cambiate insieme, come il target di «Ti pisasti?» o lo snapshot del
 * patrimonio: due formule per lo stesso target sono due target diversi a
 * seconda di dove si guarda, e in ballo c'è quanto si mangia oggi.
 *
 * Una duplicazione che la pagina ha e qui **non** c'è: `pesoPianoAl()` è la
 * copia di `getInterpolatedTarget()` di `weight-quest.html`, e in nativo quella
 * funzione esiste già ([PesoRegole.targetInterpolato]) — si chiama quella,
 * invece di riscriverla una terza volta.
 */
object CalorieRegole {

    /**
     * Un chilo di grasso corporeo vale circa 7700 kcal: è la costante che
     * trasforma «mi mancano 4 kg in 90 giorni» in «devo togliere 342 kcal al
     * giorno». È una stima, non una legge fisica — vale come ordine di
     * grandezza, ed è la ragione per cui si mostrano sempre gli ingredienti del
     * conto e non solo il totale.
     */
    const val KCAL_PER_KG = 7700.0

    /**
     * ⚠️ Queste soglie **non tagliano niente**: il target è quello che il piano
     * richiede, per basso che sia. Fanno comparire un avviso, perché un numero
     * molto basso vuol dire quasi sempre che i traguardi sono troppo
     * ravvicinati — ma la decisione resta di chi guarda, non dell'app.
     */
    private val KCAL_ATTENZIONE = mapOf("M" to 1500.0, "F" to 1200.0)

    /**
     * `cm_profile.sesso` ammette anche `'Altro'`, che Mifflin-St Jeor non
     * prevede: le sue due varianti differiscono di una costante (+5 contro
     * −161), quindi lì si usa la **via di mezzo** e lo si scrive nella
     * spiegazione del target. Fermare il conto sarebbe la scelta pulita e
     * inservibile — l'app non calcolerebbe più niente per un campo che non
     * cambia l'ordine di grandezza del risultato. La casella **vuota** invece
     * ferma il conto per davvero: non dice quale variante usare.
     */
    private fun correzioneSesso(sesso: String?): Double = when (sesso) {
        "M" -> 5.0
        "F" -> -161.0
        else -> (5.0 - 161.0) / 2.0
    }

    /** La soglia d'avviso: per «Altro» la più alta delle due, che è il verso prudente. */
    private fun sogliaAvviso(sesso: String?): Double = KCAL_ATTENZIONE[sesso] ?: 1500.0

    // ── Date ────────────────────────────────────────────────────────────

    fun giorno(iso: String?): LocalDate? = PesoRegole.giornoDa(iso)

    fun piuGiorni(quando: String, n: Long): String =
        (giorno(quando) ?: LocalDate.now()).plusDays(n).toString()

    fun giorniFra(da: String, a: String): Long {
        val x = giorno(da) ?: return 0
        val y = giorno(a) ?: return 0
        return y.toEpochDay() - x.toEpochDay()
    }

    /**
     * L'età compiuta a una certa data. Dalla data di nascita e non da un numero
     * scritto a mano: un'età archiviata è giusta un anno solo, poi comincia a
     * mentire senza dirlo.
     */
    fun etaAl(profilo: ProfiloCalorie, quando: String): Int? {
        val nascita = giorno(profilo.dataNascita) ?: return null
        val data = giorno(quando) ?: return null
        return Period.between(nascita, data).years
    }

    /**
     * Mifflin-St Jeor, la formula che le linee guida preferiscono a
     * Harris-Benedict:
     * ```
     *   uomo:  10×kg + 6.25×cm − 5×età + 5
     *   donna: 10×kg + 6.25×cm − 5×età − 161
     * ```
     */
    fun metabolismoBasale(profilo: ProfiloCalorie, pesoKg: Double?, quando: String): Double? {
        if (!profilo.completo || pesoKg == null || pesoKg <= 0) return null
        val altezza = profilo.altezzaCm ?: return null
        val eta = etaAl(profilo, quando) ?: return null
        if (eta < 10 || eta > 110) return null
        return 10 * pesoKg + 6.25 * altezza - 5 * eta + correzioneSesso(profilo.sesso)
    }

    /**
     * Il peso di un giorno: il **minimo** delle pesate di quel giorno, come fa
     * «Ti pisasti?» — ci si pesa più volte e quella che conta è la più bassa.
     * Se quel giorno non c'è nessuna pesata si ripiega sull'ultima nota
     * **prima** di quel giorno: il peso di domani non può entrare nel conto di
     * ieri, o un giorno passato cambierebbe verdetto ogni volta che si sale
     * sulla bilancia.
     */
    fun pesoAl(pesate: List<Pesata>, quando: String): Double? {
        val delGiorno = pesate.filter { it.giorno == quando }
        if (delGiorno.isNotEmpty()) return delGiorno.minOf { it.peso }
        return pesate.filter { it.giorno < quando }.maxByOrNull { it.giorno }?.peso
    }

    /** Il peso che il piano chiede per una data — la curva di «Ti pisasti?». */
    fun pesoPianoAl(traguardi: List<Traguardo>, quando: String): Double? =
        PesoRegole.targetInterpolato(traguardi, quando)

    // ── I tratti del piano ──────────────────────────────────────────────

    /**
     * Il **tratto** del piano che contiene un giorno: le due milestone che lo
     * racchiudono e il ritmo che chiedono. È il pezzo che rende diversi i
     * target di due periodi dello stesso obiettivo — un piano che chiede 3 kg
     * nel primo mese e 1 kg nei due successivi non può dare lo stesso numero in
     * entrambi.
     *
     * Torna `null` quando il piano non c'è (meno di due traguardi) o è finito:
     * in quei casi il conto ripiega sulla media fino alla fine, che è quel che
     * si può dire senza una curva.
     */
    data class Segmento(
        val inizio: String,
        val fine: String,
        val pesoInizio: Double,
        val pesoFine: Double,
        val giorni: Long,
        val numero: Int,
        val quanti: Int,
        /** Positivo = si deve perdere. Il ritmo del **tratto**, non della media. */
        val kgAlGiorno: Double,
    )

    fun segmentoDi(traguardi: List<Traguardo>, quando: String): Segmento? {
        if (traguardi.size < 2) return null
        if (quando >= traguardi.last().giorno) return null   // il piano è finito
        var i = 0
        for (k in 0 until traguardi.size - 1) {
            if (quando >= traguardi[k].giorno && quando < traguardi[k + 1].giorno) {
                i = k
                break
            }
        }
        val a = traguardi[i]
        val b = traguardi[i + 1]
        val giorni = giorniFra(a.giorno, b.giorno)
        if (giorni == 0L) return null
        return Segmento(
            inizio = a.giorno, fine = b.giorno,
            pesoInizio = a.peso, pesoFine = b.peso,
            giorni = giorni, numero = i + 1, quanti = traguardi.size - 1,
            kgAlGiorno = (a.peso - b.peso) / giorni,
        )
    }

    /** Una riga della tabella dei tratti: quanto chiederà ciascun periodo del piano. */
    data class Tratto(
        val numero: Int,
        val inizio: String,
        val fine: String,
        val giorni: Long,
        val pesoInizio: Double,
        val pesoFine: Double,
        val kg: Double,
        val kgAlGiorno: Double,
        val bmr: Double?,
        val tdee: Double?,
        val deficit: Double,
        val target: Double?,
        val corrente: Boolean,
        val passato: Boolean,
    )

    /**
     * La stima delle calorie tratto per tratto.
     *
     * ⚠️ Ogni tratto si calcola **sul peso che il piano prevede lì**, non su
     * quello di oggi: il metabolismo basale cala col peso, quindi a parità di
     * ritmo l'ultimo tratto di un dimagrimento chiede meno calorie del primo, e
     * usare il peso di oggi per tutti darebbe numeri troppo alti verso la fine.
     *
     * ⚠️ Qui il recupero dello scarto **non entra**: sono i valori «se stai sul
     * piano». Nei tratti futuri lo scarto sarebbe un'invenzione — quello di
     * domani non si conosce — e il conto vero di oggi sta nel riquadro del
     * Diario.
     */
    fun tratti(profilo: ProfiloCalorie, traguardi: List<Traguardo>, oggi: String): List<Tratto> {
        if (traguardi.size < 2 || !profilo.completo) return emptyList()
        val elenco = mutableListOf<Tratto>()
        for (i in 0 until traguardi.size - 1) {
            val a = traguardi[i]
            val b = traguardi[i + 1]
            val giorni = giorniFra(a.giorno, b.giorno)
            if (giorni == 0L) continue
            val pesoMedio = (a.peso + b.peso) / 2
            val bmr = metabolismoBasale(profilo, pesoMedio, a.giorno)
            val tdee = bmr?.times(profilo.attivita)
            val kgAlGiorno = (a.peso - b.peso) / giorni
            val deficit = kgAlGiorno * KCAL_PER_KG
            elenco += Tratto(
                numero = i + 1, inizio = a.giorno, fine = b.giorno, giorni = giorni,
                pesoInizio = a.peso, pesoFine = b.peso, kg = a.peso - b.peso,
                kgAlGiorno = kgAlGiorno, bmr = bmr, tdee = tdee,
                deficit = deficit, target = tdee?.minus(deficit),
                corrente = oggi >= a.giorno && oggi < b.giorno,
                passato = oggi >= b.giorno,
            )
        }
        return elenco
    }

    /**
     * Il target «se stai sul piano» del tratto che contiene un giorno — quello
     * della tabella, senza il recupero dello scarto. È la base da cui parte il
     * target proiettato di un giorno futuro.
     */
    fun targetTrattoAl(quando: String, tratti: List<Tratto>): Double? {
        if (tratti.isEmpty()) return null
        val t = tratti.firstOrNull { quando >= it.inizio && quando < it.fine }
            ?: tratti.last().takeIf { quando >= it.fine }
        return t?.target
    }

    // ── Il target di un giorno ──────────────────────────────────────────

    /**
     * Perché il target è quello che è — e perché non c'è, quando non c'è. «Non
     * lo so» e «zero» sono due cose diverse: un target a zero farebbe sembrare
     * sforata ogni giornata.
     */
    enum class Motivo { PROFILO, PESO, MANTENIMENTO, OBIETTIVO, RAGGIUNTO, CONGELATO }

    data class Target(
        val ok: Boolean = false,
        val motivo: Motivo? = null,
        val kcal: Double? = null,
        val bmr: Double? = null,
        val tdee: Double? = null,
        val deficit: Double = 0.0,
        val peso: Double? = null,
        val pesoPiano: Double? = null,
        val pesoFinale: Double? = null,
        val giorniRimasti: Long? = null,
        val kgDaPerdere: Double? = null,
        val congelato: Boolean = false,
        val avvisi: List<String> = emptyList(),
        /**
         * Il deficit nei suoi due addendi: il ritmo che il tratto di piano
         * chiede e il recupero dello scarto accumulato. Servono a schermo,
         * perché un numero solo non direbbe se oggi è più stretto perché il
         * piano corre o perché si è rimasti indietro.
         */
        val segmento: Segmento? = null,
        val deficitPiano: Double? = null,
        val scartoKg: Double? = null,
        val recupero: Double? = null,
    )

    /**
     * Il conto vero e proprio.
     *
     * ⚠️ **Il deficit è la somma di DUE addendi, e la separazione è la
     * funzionalità.**
     *
     * 1. il **ritmo del tratto** in cui si è oggi. Un obiettivo coi traguardi
     *    ravvicinati all'inizio e radi dopo chiede molto adesso e poco poi:
     *    spalmare tutta la perdita residua su tutto il tempo residuo dà lo
     *    stesso numero nei due tratti, e fa restare indietro proprio dove il
     *    piano correva. Un piano che chiede 0,70 kg a settimana vuole ~770 kcal
     *    al giorno, non i 322 della media.
     * 2. il **recupero dello scarto** già accumulato, spalmato sui giorni che
     *    restano **in tutto** e non su quelli del tratto: è l'addendo che
     *    stringe il target da sé quando si è indietro, e sui soli giorni del
     *    tratto sarebbe feroce a ridosso di un traguardo — due chili in cinque
     *    giorni non sono un obiettivo.
     *
     * Senza il primo il piano non si sente, senza il secondo non ci si accorge
     * di essere rimasti indietro. La somma **non scende sotto zero**: essere
     * molto avanti allenta il target fino al mantenimento, non oltre — un
     * deficit negativo sarebbe l'app che invita a mangiare di più.
     */
    fun calcolaTarget(
        profilo: ProfiloCalorie,
        pesate: List<Pesata>,
        obiettivo: Obiettivo?,
        quando: String,
    ): Target {
        if (!profilo.completo) return Target(motivo = Motivo.PROFILO)

        val peso = pesoAl(pesate, quando) ?: return Target(motivo = Motivo.PESO)
        val bmr = metabolismoBasale(profilo, peso, quando) ?: return Target(motivo = Motivo.PROFILO)
        val tdee = bmr * profilo.attivita

        // Senza obiettivo attivo non si inventa un dimagrimento: il target è il
        // mantenimento, e la pagina lo dice invece di far finta che ci sia un piano.
        val mantenimento = Target(
            ok = true, motivo = Motivo.MANTENIMENTO, kcal = tdee,
            bmr = bmr, tdee = tdee, peso = peso,
        )
        if (obiettivo == null) return mantenimento

        val traguardi = obiettivo.traguardi
        val pesoPiano = pesoPianoAl(traguardi, quando)

        /* ⚠️ L'ultimo traguardo vale come peso finale **solo se i traguardi
           sono almeno due**: uno solo è il punto di partenza, non la meta, e
           prenderlo per tale fa credere l'obiettivo già raggiunto (peso di oggi
           ≤ «finale») e azzera il deficit senza dire niente. */
        val finale = if (traguardi.size >= 2) traguardi.last().peso else obiettivo.pesoFinale
        val fine = obiettivo.fine.takeIf { it.isNotBlank() } ?: traguardi.lastOrNull()?.giorno

        // Almeno un giorno: a fine obiettivo la divisione per zero darebbe un
        // deficit infinito, e a obiettivo scaduto un deficit negativo — cioè un
        // invito a mangiare di più proprio il giorno in cui il tempo è finito.
        val giorniRimasti = fine?.let { max(1L, giorniFra(quando, it)) }
        if (finale == null || giorniRimasti == null) return mantenimento.copy(pesoPiano = pesoPiano)

        val kgDaPerdere = max(0.0, peso - finale)
        val segmento = segmentoDi(traguardi, quando)

        var deficitPiano: Double? = null
        var scartoKg: Double? = null
        var recupero: Double? = null
        val deficit: Double
        if (segmento != null && pesoPiano != null) {
            deficitPiano = segmento.kgAlGiorno * KCAL_PER_KG
            scartoKg = peso - pesoPiano
            recupero = scartoKg * KCAL_PER_KG / giorniRimasti
            deficit = max(0.0, deficitPiano + recupero)
        } else {
            // Nessuna curva (meno di due traguardi) o piano già finito: resta la
            // media fino alla fine, che è tutto quel che si può dire senza
            // sapere come il piano voleva distribuirla.
            deficit = kgDaPerdere * KCAL_PER_KG / giorniRimasti
        }

        val kcal = tdee - deficit

        // ⚠️ Nessun taglio: solo un avviso. Il numero resta quello che il piano
        // chiede — anche negativo, che è il modo più chiaro di dire che in quei
        // giorni quel peso non ci si arriva nemmeno digiunando.
        val avvisi = mutableListOf<String>()
        val soglia = sogliaAvviso(profilo.sesso)
        if (kcal <= 0) {
            avvisi += "Il piano chiede più di quanto il corpo consuma in un giorno: il target esce " +
                "negativo (${kcalIt(kcal)} kcal). In $giorniRimasti " +
                (if (giorniRimasti == 1L) "giorno" else "giorni") +
                " quei ${kgIt(kgDaPerdere)} kg non si perdono — la data di fine va spostata in «Ti pisasti?»."
        } else if (kcal < soglia) {
            avvisi += "Target sotto le ${kcalIt(soglia)} kcal: è poco, e quasi sempre vuol dire che i " +
                "traguardi sono troppo ravvicinati. Il numero non è stato ritoccato — se lo vuoi più " +
                "largo, allarga il piano in «Ti pisasti?»."
        } else if (deficit > 1000) {
            avvisi += "Il piano chiede un deficit di ${kcalIt(deficit)} kcal al giorno, cioè oltre un " +
                "chilo a settimana. È tanto: se non regge, sposta la data di fine invece di saltare i pasti."
        }

        return Target(
            ok = true,
            motivo = if (kgDaPerdere > 0) Motivo.OBIETTIVO else Motivo.RAGGIUNTO,
            kcal = kcal, bmr = bmr, tdee = tdee, deficit = deficit,
            peso = peso, pesoPiano = pesoPiano, pesoFinale = finale,
            giorniRimasti = giorniRimasti, kgDaPerdere = kgDaPerdere,
            avvisi = avvisi,
            segmento = segmento, deficitPiano = deficitPiano,
            scartoKg = scartoKg, recupero = recupero,
        )
    }

    /**
     * Il target da mostrare per un giorno: quello **congelato** se c'è,
     * altrimenti quello calcolato ora. La riga congelata vince sempre — è il
     * senso stesso del congelamento: spostare un traguardo in «Ti pisasti?»
     * domani non deve riscrivere il giudizio su un giorno già passato.
     */
    fun targetDelGiorno(
        profilo: ProfiloCalorie,
        pesate: List<Pesata>,
        obiettivo: Obiettivo?,
        congelati: Map<String, GiornoCongelato>,
        quando: String,
    ): Target {
        congelati[quando]?.let { riga ->
            return Target(
                ok = true, motivo = Motivo.CONGELATO, congelato = true,
                kcal = riga.target, bmr = riga.bmr, tdee = riga.tdee,
                deficit = riga.deficit ?: 0.0, peso = riga.peso,
                pesoPiano = obiettivo?.let { pesoPianoAl(it.traguardi, quando) },
            )
        }
        return calcolaTarget(profilo, pesate, obiettivo, quando)
    }

    // ── L'arco della dieta e il giorno per giorno ───────────────────────

    /**
     * Il primo e l'ultimo giorno del piano: i traguardi quando ci sono (sono
     * loro a disegnare la curva), altrimenti le date dell'obiettivo.
     *
     * ⚠️ Il primo è **tirato in avanti fino alla finestra di caricamento**: una
     * riga che dice «niente segnato» per un giorno di cui non si sono lette le
     * righe è una bugia, e sembra un archivio che si svuota da solo. Quando
     * succede, `tagliato` lo fa dire a schermo.
     */
    data class Arco(
        val primo: String,
        val ultimo: String,
        val primoVero: String,
        val tagliato: Boolean,
    )

    fun arcoDellaDieta(obiettivo: Obiettivo?, oggi: String): Arco? {
        if (obiettivo == null) return null
        val traguardi = obiettivo.traguardi
        val primo = (traguardi.firstOrNull()?.giorno ?: obiettivo.inizio).take(10)
        val ultimo = (if (traguardi.size >= 2) traguardi.last().giorno else obiettivo.fine).take(10)
        if (primo.isBlank() || ultimo.isBlank()) return null
        val finestra = piuGiorni(oggi, -GIORNI_STORICO)
        return Arco(
            primo = if (primo < finestra) finestra else primo,
            ultimo = ultimo,
            primoVero = primo,
            tagliato = primo < finestra,
        )
    }

    /** Una giornata della dieta, passata o futura. */
    data class GiornoDieta(
        val giorno: String,
        val kcal: Double?,
        val target: Double?,
        val scarto: Double?,
        val futuro: Boolean,
        val oggi: Boolean,
        val riporto: Double?,
        val peso: Double?,
        val pesoPiano: Double?,
        val righe: Int,
    )

    data class Piano(
        val giorni: List<GiornoDieta> = emptyList(),
        val saldo: Double = 0.0,
        val restanti: Int = 0,
        val alGiorno: Double = 0.0,
    )

    /**
     * Giorno per giorno dal primo all'ultimo del piano.
     *
     * ⚠️ Per i giorni **futuri** il target parte da quello del tratto e si
     * porta dietro lo scarto accumulato, che si **spalma su tutti i giorni che
     * restano** — ogni giorno futuro prende la stessa fetta, così il numero si
     * controlla a mente («900 in 80 giorni, undici al giorno»). Scaricarlo tutto
     * sul giorno dopo, com'era fino alla v1.12.1 della pagina, faceva scendere
     * il giorno seguente a 288 kcal dopo uno sforo di 900: un target che non si
     * può seguire non lo si segue.
     *
     * ⚠️ Un giorno **senza righe non entra nel saldo**: non è un digiuno, è un
     * giorno non segnato. E da **oggi** entra solo lo **sforo**, mai il
     * risparmio: alle sette di sera il diario non è finito, e leggere quel che
     * non è ancora stato segnato come un risparmio regalerebbe calorie che
     * nessuno si è guadagnato. Lo sforo invece è già successo e non si disfa.
     *
     * ⚠️ Il saldo del riquadro e quello che si spalma sono lo **stesso** numero,
     * calcolato qui una volta sola: due conti separati sarebbero due verità
     * sullo stesso dato, e chi legge non saprebbe quale credere.
     */
    fun giorniDellaDieta(
        profilo: ProfiloCalorie,
        pesate: List<Pesata>,
        obiettivo: Obiettivo?,
        congelati: Map<String, GiornoCongelato>,
        righePerGiorno: Map<String, List<RigaDiario>>,
        oggi: String,
    ): Piano {
        val arco = arcoDellaDieta(obiettivo, oggi) ?: return Piano()
        val tratti = tratti(profilo, obiettivo?.traguardi.orEmpty(), oggi)
        val elenco = mutableListOf<GiornoDieta>()

        // Il saldo si può sapere solo dopo aver visto tutti i giorni chiusi, e
        // quanti giorni restano solo dopo averli contati: la spalmatura è quindi
        // un secondo giro, non si può fare per strada.
        var saldo = 0.0
        var g = arco.primo
        var guardia = 0
        while (g <= arco.ultimo && guardia < 4000) {
            guardia++
            val rr = righePerGiorno[g].orEmpty()
            val kcal = if (rr.isEmpty()) null else rr.sumOf { it.kcalRiga }
            val futuro = g > oggi
            val target = if (futuro) null
            else targetDelGiorno(profilo, pesate, obiettivo, congelati, g).takeIf { it.ok }?.kcal
            val scarto = if (kcal != null && target != null) kcal - target else null
            if (!futuro && scarto != null) saldo += if (g == oggi) max(0.0, scarto) else scarto
            elenco += GiornoDieta(
                giorno = g,
                kcal = kcal,
                target = target,
                scarto = scarto,
                futuro = futuro,
                oggi = g == oggi,
                riporto = null,
                // ⚠️ Nel futuro il peso non si trascina: `pesoAl` ripiegherebbe
                // sull'ultima pesata nota, e una linea piatta fino alla fine del
                // piano sembrerebbe una previsione che nessuno ha fatto.
                peso = if (futuro) null else pesoAl(pesate, g),
                pesoPiano = obiettivo?.let { pesoPianoAl(it.traguardi, g) },
                righe = rr.size,
            )
            g = piuGiorni(g, 1)
        }

        val futuri = elenco.count { it.futuro }
        val alGiorno = if (futuri > 0) saldo / futuri else 0.0
        val conRiporto = elenco.map { riga ->
            if (!riga.futuro) return@map riga
            val base = targetTrattoAl(riga.giorno, tratti) ?: return@map riga
            riga.copy(riporto = -alGiorno, target = (base - alGiorno).roundToInt().toDouble())
        }

        return Piano(giorni = conRiporto, saldo = saldo, restanti = futuri, alGiorno = alGiorno)
    }

    /**
     * Il giorno più indietro a cui il diario si può spostare: il **primo giorno
     * della dieta**, mai prima della finestra di caricamento.
     *
     * Il `max` non è prudenza: senza, dal 121° giorno indietro ogni giornata
     * comparirebbe vuota — non perché lo sia, ma perché le sue righe non sono
     * state lette. Un archivio che sembra svuotarsi da solo è peggio di un
     * pulsante spento. Senza obiettivo attivo resta la sola finestra, che è
     * tutto quel che si sa.
     */
    fun primoGiornoDiario(obiettivo: Obiettivo?, oggi: String): String {
        val finestra = piuGiorni(oggi, -GIORNI_STORICO)
        val inizio = obiettivo?.inizio?.take(10)?.takeIf { it.isNotBlank() }
        return if (inizio != null && inizio > finestra) inizio else finestra
    }

    // ── I totali di una giornata ────────────────────────────────────────

    data class Totali(
        val kcal: Double = 0.0,
        val proteine: Double = 0.0,
        val grassi: Double = 0.0,
        val saturi: Double = 0.0,
        val carboidrati: Double = 0.0,
        val zuccheri: Double = 0.0,
        val fibre: Double = 0.0,
        val sale: Double = 0.0,
    )

    fun totali(righe: List<RigaDiario>) = Totali(
        kcal = righe.sumOf { it.kcalRiga },
        proteine = righe.sumOf { it.proteineRiga },
        grassi = righe.sumOf { it.grassiRiga },
        saturi = righe.sumOf { it.saturiRiga },
        carboidrati = righe.sumOf { it.carboidratiRiga },
        zuccheri = righe.sumOf { it.zuccheriRiga },
        fibre = righe.sumOf { it.fibreRiga },
        sale = righe.sumOf { it.saleRiga },
    )
}

/* ── Come si scrivono i numeri ─────────────────────────────────────────── */

/** Le calorie: intere, coi separatori all'italiana; `—` quando non ci sono. */
internal fun kcalIt(valore: Double?): String =
    valore?.let { String.format(java.util.Locale.ITALY, "%,d", it.roundToInt()) } ?: "—"

/** Un peso o dei grammi: un decimale, virgola italiana. */
internal fun kgIt(valore: Double?): String =
    valore?.let { String.format(java.util.Locale.ITALY, "%.1f", it) } ?: "—"

/**
 * Il ritmo di un tratto va a **due** decimali: a uno solo, 0,12 kg a settimana
 * diventa «0,1» e un tratto lento è indistinguibile da un tratto fermo.
 */
internal fun ritmoIt(valore: Double?): String =
    valore?.let { String.format(java.util.Locale.ITALY, "%.2f", it) } ?: "—"

/** `2026-08-31` → `31/08`, per le tabelle strette. */
internal fun dataBreve(iso: String): String {
    val pezzi = iso.take(10).split("-")
    return if (pezzi.size == 3) "${pezzi[2]}/${pezzi[1]}" else iso
}

/** `2026-08-31` → `lun 31 agosto`, come l'intestazione del diario nella pagina. */
internal fun dataLunga(iso: String): String {
    val data = CalorieRegole.giorno(iso) ?: return iso
    val giorni = listOf("lun", "mar", "mer", "gio", "ven", "sab", "dom")
    val mesi = listOf(
        "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
        "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre",
    )
    return "${giorni[data.dayOfWeek.value - 1]} ${data.dayOfMonth} ${mesi[data.monthValue - 1]}"
}

/** Il segno davanti a uno scarto: `+` di troppo, `−` in meno. */
internal fun conSegno(valore: Double): String =
    (if (valore > 0) "+" else if (valore < 0) "−" else "") + kcalIt(abs(valore))

internal fun percentuale(parte: Double, totale: Double): Double =
    if (totale <= 0) 0.0 else min(100.0, parte / totale * 100.0)
