package com.garsal.appsphere.peso

import java.time.LocalDate

/**
 * Le regole di Weight Quest, ricalcate da `weight-quest.html`.
 *
 * ⚠️ **Sono la funzionalità, non un dettaglio di presentazione**: il target di
 * un giorno, i punti che quel giorno vale e il cumulativo devono dare lo stesso
 * numero nelle due app, o la stessa giornata risulta guadagnata da una parte e
 * persa dall'altra. Se cambia una di queste funzioni, cambia anche la gemella
 * in `weight-quest.html` (`getInterpolatedTarget`,
 * `getInterpolatedWeightFromSeries`, `updateWeightTable`,
 * `calculateTotalScores`).
 */
object PesoRegole {

    /**
     * Il target del giorno, interpolato fra i traguardi — `getInterpolatedTarget`.
     *
     * Con meno di due traguardi non c'è nessuna curva e il target non esiste:
     * si torna `null`, che a schermo diventa un trattino. Prima del primo
     * traguardo e dopo l'ultimo vale il valore estremo, senza estrapolare.
     */
    fun targetInterpolato(traguardi: List<Traguardo>, giorno: String): Double? {
        if (traguardi.size < 2) return null
        val data = giornoDa(giorno) ?: return null
        val primo = traguardi.first()
        val ultimo = traguardi.last()
        val dataPrimo = giornoDa(primo.giorno) ?: return null
        val dataUltimo = giornoDa(ultimo.giorno) ?: return null

        if (data.isBefore(dataPrimo)) return primo.peso
        if (data.isAfter(dataUltimo)) return ultimo.peso

        for (i in 0 until traguardi.size - 1) {
            val a = traguardi[i]
            val b = traguardi[i + 1]
            val da = giornoDa(a.giorno) ?: continue
            val ab = giornoDa(b.giorno) ?: continue
            if (!data.isBefore(da) && !data.isAfter(ab)) {
                val giorniTotali = (ab.toEpochDay() - da.toEpochDay()).toDouble()
                if (giorniTotali <= 0.0) return a.peso
                val trascorsi = (data.toEpochDay() - da.toEpochDay()).toDouble()
                val quota = trascorsi / giorniTotali
                return arrotonda(a.peso + (b.peso - a.peso) * quota, 2)
            }
        }
        return null
    }

    /**
     * Il peso di un giorno **senza pesate**, interpolato fra la pesata prima e
     * quella dopo — `getInterpolatedWeightFromSeries`. Non è il target: è la
     * stima di quanto si pesava quel giorno, e serve per non lasciare buchi nel
     * conto dei punti quando ci si dimentica di salire sulla bilancia.
     *
     * Fuori dalla serie non si estrapola: prima della prima pesata vale la
     * prima, dopo l'ultima vale l'ultima.
     */
    fun pesoInterpolato(minimi: List<Pair<String, Double>>, giorno: String): Double? {
        if (minimi.isEmpty()) return null
        if (minimi.size == 1) return minimi.first().second

        var prima: Pair<String, Double>? = null
        var dopo: Pair<String, Double>? = null
        for (m in minimi) {
            if (m.first < giorno) prima = m
            else if (m.first > giorno && dopo == null) { dopo = m; break }
        }

        return when {
            prima != null && dopo != null -> {
                val da = giornoDa(prima.first) ?: return prima.second
                val a = giornoDa(dopo.first) ?: return prima.second
                val totale = (a.toEpochDay() - da.toEpochDay()).toDouble()
                if (totale <= 0.0) return prima.second
                val data = giornoDa(giorno) ?: return prima.second
                val quota = (data.toEpochDay() - da.toEpochDay()) / totale
                arrotonda(prima.second + (dopo.second - prima.second) * quota, 2)
            }
            prima != null -> prima.second
            else -> dopo?.second
        }
    }

    /**
     * I punti di una giornata: bonus se si è sotto (o pari) al target, malus
     * se si è sopra. **Il confronto si fa a un decimale**, come nel web
     * (`Math.round(x * 10) / 10`): 74,04 contro un target di 74,0 è una
     * giornata vinta, e con un confronto a piena precisione sarebbe persa.
     */
    fun punti(peso: Double, target: Double, bonus: Int, malus: Int): Int =
        if (arrotonda(peso, 1) <= arrotonda(target, 1)) bonus else -malus

    /** Il minimo di ogni giornata, in ordine di data: la pesata del mattino. */
    fun minimiGiornalieri(pesate: List<Pesata>): List<Pair<String, Double>> =
        pesate.groupBy { it.giorno }
            .map { (giorno, righe) -> giorno to righe.minOf { it.peso } }
            .sortedBy { it.first }

    /**
     * Una riga della tabella: una giornata.
     *
     * `interpolata` distingue i giorni in cui si è saliti sulla bilancia da
     * quelli ricostruiti: valgono punti come gli altri — è la scelta del web,
     * e toglierla cambierebbe il punteggio — ma vanno detti, altrimenti sembra
     * che ci sia una pesata che non c'è.
     */
    data class RigaGiorno(
        val giorno: String,
        val target: Double?,
        val minimo: Double?,
        val massimo: Double?,
        val oraMinimo: String?,
        val oraMassimo: String?,
        val punti: Int?,
        val cumulativo: Int?,
        val interpolata: Boolean,
    )

    /**
     * La tabella giorno per giorno, dalla più recente — `updateWeightTable`.
     *
     * Il cumulativo si costruisce in ordine cronologico sui soli giorni
     * dell'obiettivo (pesate vere **più** giorni interpolati fino a oggi), e i
     * giorni fuori dal periodo restano senza punti: si vedono, ma non contano.
     */
    fun tabella(
        pesate: List<Pesata>,
        obiettivo: Obiettivo?,
        oggi: LocalDate = LocalDate.now(),
    ): List<RigaGiorno> {
        val perGiorno = pesate.groupBy { it.giorno }
        val minimi = minimiGiornalieri(pesate)
        val oggiStr = oggi.toString()

        val interpolati = giorniInterpolati(perGiorno.keys, obiettivo, oggiStr)

        val puntiPerGiorno = mutableMapOf<String, Int>()
        val cumulativi = mutableMapOf<String, Int>()
        if (obiettivo != null) {
            var somma = 0
            (perGiorno.keys + interpolati)
                .filter { it >= obiettivo.inizio && it <= obiettivo.fine }
                .sorted()
                .forEach { giorno ->
                    val interpolato = giorno in interpolati
                    val target: Double?
                    val peso: Double?
                    if (interpolato) {
                        target = targetInterpolato(obiettivo.traguardi, giorno)
                        peso = pesoInterpolato(minimi, giorno)
                    } else {
                        val righe = perGiorno.getValue(giorno)
                        val minima = righe.minByOrNull { it.peso }
                        target = minima?.target ?: targetInterpolato(obiettivo.traguardi, giorno)
                        peso = minima?.peso
                    }
                    if (target == null || peso == null) return@forEach

                    val delGiorno = punti(peso, target, obiettivo.bonusGiornaliero, obiettivo.malusGiornaliero)
                    somma += delGiorno
                    puntiPerGiorno[giorno] = delGiorno
                    cumulativi[giorno] = somma
                }
        }

        return (perGiorno.keys + interpolati).sortedDescending().map { giorno ->
            val righe = perGiorno[giorno]
            val minima = righe?.minByOrNull { it.peso }
            val massima = righe?.maxByOrNull { it.peso }
            val interpolato = giorno in interpolati
            RigaGiorno(
                giorno = giorno,
                target = minima?.target
                    ?: obiettivo?.let { targetInterpolato(it.traguardi, giorno) },
                minimo = if (interpolato) pesoInterpolato(minimi, giorno) else minima?.peso,
                massimo = if (interpolato) null else massima?.peso,
                oraMinimo = minima?.ora,
                oraMassimo = massima?.ora,
                punti = puntiPerGiorno[giorno],
                cumulativo = cumulativi[giorno],
                interpolata = interpolato,
            )
        }
    }

    /**
     * I giorni dell'obiettivo senza nessuna pesata, fino a oggi: quelli che il
     * web ricostruisce per interpolazione. **Fino a oggi e non fino alla fine
     * dell'obiettivo**: i giorni che devono ancora arrivare non si giudicano.
     */
    private fun giorniInterpolati(
        conPesate: Set<String>,
        obiettivo: Obiettivo?,
        oggiStr: String,
    ): Set<String> {
        if (obiettivo == null) return emptySet()
        val inizio = giornoDa(obiettivo.inizio) ?: return emptySet()
        val ultimo = giornoDa(minOf(obiettivo.fine, oggiStr)) ?: return emptySet()
        if (ultimo.isBefore(inizio)) return emptySet()
        if (obiettivo.traguardi.size < 2) return emptySet()

        val giorni = mutableSetOf<String>()
        var cursore = inizio
        while (!cursore.isAfter(ultimo)) {
            val giorno = cursore.toString()
            if (giorno !in conPesate) giorni += giorno
            cursore = cursore.plusDays(1)
        }
        return giorni
    }

    /**
     * Il minimo di oggi; se oggi non ci si è pesati, **l'ultima pesata nota**.
     *
     * Il ripiego è quello di `getTodayMinWeight`, che scorre `data` e prende il
     * primo peso valido: là la lista arriva ordinata per data **discendente**
     * (`fetchAllWeights('date', false)`), quindi «il primo» è il più recente.
     * Qui le pesate sono in ordine crescente, e il più recente è l'ultimo — la
     * stessa pesata, presa dal capo opposto della lista.
     */
    fun minimoDiOggi(pesate: List<Pesata>, oggi: LocalDate = LocalDate.now()): Double? {
        val oggiStr = oggi.toString()
        val diOggi = pesate.filter { it.giorno == oggiStr }
        if (diOggi.isNotEmpty()) return diOggi.minOf { it.peso }
        return pesate.lastOrNull()?.peso
    }

    /**
     * Il minimo di **oggi**, `null` se non ci si è pesati oggi — a differenza
     * di [minimoDiOggi] non ripiega sull'ultima pesata nota. È il controllo
     * di `closeObjective('success')` per un obiettivo «perdere»: chiudere con
     * successo vuole una pesata di oggi, non una vecchia.
     */
    fun minimoStrettoOggi(pesate: List<Pesata>, oggi: LocalDate = LocalDate.now()): Double? {
        val diOggi = pesate.filter { it.giorno == oggi.toString() }
        return if (diOggi.isEmpty()) null else diOggi.minOf { it.peso }
    }

    /**
     * Il massimo peso registrato nel periodo `[inizio, fine]`, estremi
     * compresi — il controllo di `closeObjective('success')` per un
     * obiettivo «mantenere»: il peggiore del periodo deve stare sotto il
     * peso stabilito.
     */
    fun massimoNelPeriodo(pesate: List<Pesata>, inizio: String, fine: String): Double? =
        pesate.filter { it.giorno >= inizio && it.giorno <= fine }.maxOfOrNull { it.peso }

    fun arrotonda(valore: Double, decimali: Int): Double {
        val fattore = Math.pow(10.0, decimali.toDouble())
        return Math.round(valore * fattore) / fattore
    }

    /**
     * Le soglie intere dei traguardi premio, dalla più facile alla più
     * difficile — `getMilestoneThresholds` nel web. Con `pesoIniziale` non
     * sopra `pesoFinale` (o uno dei due mancante) non c'è nessuna soglia.
     */
    fun sogliePremio(pesoIniziale: Double?, pesoFinale: Double?): List<Int> {
        if (pesoIniziale == null || pesoFinale == null || pesoIniziale <= pesoFinale) return emptyList()
        val top = Math.floor(pesoIniziale).toInt() - 1
        val bot = Math.ceil(pesoFinale).toInt()
        if (top < bot) return emptyList()
        return (top downTo bot).toList()
    }

    /**
     * Distribuzione crescente dei punti fra le soglie —
     * `getMilestonePtsDistribution`: la soglia più lontana (l'ultima, la più
     * difficile) vale di più. L'ultima prende il residuo, per non perdere
     * punti negli arrotondamenti.
     */
    fun distribuzionePunti(totalPts: Int, n: Int): List<Int> {
        if (n <= 0 || totalPts <= 0) return emptyList()
        val sommaFattori = n * (n + 1) / 2.0
        val punti = mutableListOf<Int>()
        var assegnati = 0
        for (i in 0 until n) {
            val p = if (i < n - 1) Math.round(totalPts * (i + 1) / sommaFattori).toInt()
                    else totalPts - assegnati
            punti += p
            assegnati += p
        }
        return punti
    }

    /** `2026-08-14` → data; qualunque altra cosa → null, senza sollevare. */
    fun giornoDa(iso: String?): LocalDate? =
        iso?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

/** `2026-08-14` → `14/08/2026`, come in tutte le app di casa. */
internal fun dataItaliana(iso: String): String {
    val pezzi = iso.take(10).split("-")
    if (pezzi.size != 3) return iso
    return "${pezzi[2]}/${pezzi[1]}/${pezzi[0]}"
}

/** Un peso come lo scrive la pagina: un decimale, virgola italiana. */
internal fun kg(valore: Double?): String =
    valore?.let { String.format(java.util.Locale.ITALY, "%.1f", it) } ?: "–"
