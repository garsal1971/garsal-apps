package com.garsal.appsphere.home

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Posizionamento delle bolle, portato da `index.html` (`sizeOf`,
 * `initialPlace`, `settlePositions`, `resolveCollisions`).
 *
 * Una differenza voluta rispetto al web: là i cerchi devono anche scansare il
 * pannello del punteggio, che galleggia sopra la stessa area (`pushFromPanel`).
 * Qui il totale sta nella barra in alto, fuori dall'area delle bolle, quindi
 * non c'è nessun rettangolo da evitare e quel pezzo non è stato portato.
 */
object BubbleLayout {

    private const val PAD = 12f
    private const val GAP = 8f

    /** Una bolla in movimento: raggio fisso, centro che cambia. */
    data class Nodo(
        val indice: Int,
        var x: Float,
        var y: Float,
        val r: Float,
    )

    /**
     * Diametro proporzionale al punteggio: `area = (score/maxScore) * MAX_AREA`,
     * con un minimo di 6 cm² perché la bolla resti toccabile anche a punteggio
     * zero. Identico al web, densità inclusa.
     */
    fun diametro(punteggio: Int, punteggioMax: Int, densita: Float): Float {
        val pxPerCm = densita * 160f / 2.54f      // dp→px del web: 96 dpi; qui la densità vera
        val areaMin = 6f * pxPerCm * pxPerCm
        val diametroMax = 220f * densita
        val areaMax = PI.toFloat() * (diametroMax / 2f) * (diametroMax / 2f)
        val max = max(punteggioMax, 1)
        val area = max(areaMin, (punteggio.toFloat() / max) * areaMax)
        return 2f * sqrt(area / PI.toFloat())
    }

    /**
     * Prima collocazione: si parte stretti al centro e si allarga il raggio di
     * ricerca finché non si trova un posto che non tocca nessuno.
     */
    fun collocazioneIniziale(
        r: Float,
        gia: List<Nodo>,
        w: Float,
        h: Float,
        random: Random = Random.Default,
    ): Pair<Float, Float> {
        val cx = w / 2f
        val cy = h / 2f
        repeat(200) { tentativo ->
            val ampiezza = r + tentativo * (r * 0.5f)
            repeat(30) {
                val angolo = random.nextFloat() * 2f * PI.toFloat()
                val distanza = random.nextFloat() * ampiezza
                val x = (cx + cos(angolo) * distanza).coerceIn(r + PAD, max(r + PAD, w - r - PAD))
                val y = (cy + sin(angolo) * distanza).coerceIn(r + PAD, max(r + PAD, h - r - PAD))
                val libero = gia.all { hypot(x - it.x, y - it.y) >= r + it.r + GAP }
                if (libero) return x to y
            }
        }
        return cx to cy
    }

    /**
     * Assesta le posizioni dopo il primo disegno: si separano le coppie che si
     * toccano finché nessuno si muove più (o dopo 300 giri).
     */
    fun assesta(nodi: List<Nodo>, w: Float, h: Float) {
        repeat(300) {
            val prima = nodi.map { it.x to it.y }
            separaCoppie(nodi, fissato = null)
            nodi.forEach { limita(it, w, h) }
            val spostamento = nodi.mapIndexed { i, n ->
                abs(n.x - prima[i].first) + abs(n.y - prima[i].second)
            }.sum()
            if (spostamento < 0.01f) return
        }
    }

    /**
     * Collisioni durante il trascinamento, in due fasi alternate:
     * prima si spingono via gli altri tenendo fermo quello in mano, poi si
     * misura quanto gli altri respingono lui e lo si sposta di conseguenza.
     *
     * La convergenza si misura sullo spostamento netto rispetto allo scatto di
     * inizio giro, non sulla singola spinta: una bolla schiacciata fra quella
     * trascinata e il bordo riceve spinte non nulle che si annullano fra loro,
     * e senza questo accorgimento il ciclo non finirebbe mai.
     */
    fun risolviTrascinamento(nodi: List<Nodo>, trascinato: Int, w: Float, h: Float) {
        val dn = nodi.firstOrNull { it.indice == trascinato } ?: return

        repeat(8) {
            // ── Fase 1: si spostano gli altri, il trascinato sta fermo ──
            // L'etichetta serve a uscire davvero dal ciclo appena si è
            // assestato: un `return@repeat` salterebbe solo il giro corrente.
            run fase1@{
                repeat(60) {
                    val prima = nodi.map { it.x to it.y }
                    separaCoppie(nodi, fissato = trascinato)
                    nodi.forEach { if (it.indice != trascinato) limita(it, w, h) }
                    val spostamento = nodi.mapIndexed { i, n ->
                        if (n.indice == trascinato) 0f
                        else abs(n.x - prima[i].first) + abs(n.y - prima[i].second)
                    }.sum()
                    if (spostamento < 0.01f) return@fase1
                }
            }

            // ── Fase 2: tutte le spinte sul trascinato, sommate insieme ──
            // Sommarle e applicarle in blocco evita che l'ordine con cui si
            // scorrono le bolle sbilanci la direzione.
            var spintaX = 0f
            var spintaY = 0f
            nodi.forEach { n ->
                if (n.indice == trascinato) return@forEach
                val minima = dn.r + n.r + GAP
                val dx = n.x - dn.x
                val dy = n.y - dn.y
                val d = hypot(dx, dy).takeIf { it > 0f } ?: 0.01f
                if (d < minima) {
                    val sovrapposizione = minima - d
                    spintaX -= (dx / d) * sovrapposizione
                    spintaY -= (dy / d) * sovrapposizione
                }
            }
            if (hypot(spintaX, spintaY) < 0.01f) return   // è libero: finito

            val primaX = dn.x
            val primaY = dn.y
            dn.x += spintaX
            dn.y += spintaY
            limita(dn, w, h)
            if (abs(dn.x - primaX) + abs(dn.y - primaY) < 0.01f) return
        }
    }

    /**
     * Un giro di separazione su tutte le coppie che si sovrappongono.
     * Se una delle due è quella in mano, si sposta solo l'altra e per l'intera
     * sovrapposizione; altrimenti si divide a metà.
     */
    private fun separaCoppie(nodi: List<Nodo>, fissato: Int?) {
        for (a in nodi.indices) {
            for (b in a + 1 until nodi.size) {
                val na = nodi[a]
                val nb = nodi[b]
                val minima = na.r + nb.r + GAP
                val dx = nb.x - na.x
                val dy = nb.y - na.y
                val d = hypot(dx, dy).takeIf { it > 0f } ?: 0.01f
                if (d >= minima) continue
                val sovrapposizione = minima - d
                val nx = dx / d
                val ny = dy / d
                when (fissato) {
                    na.indice -> { nb.x += nx * sovrapposizione; nb.y += ny * sovrapposizione }
                    nb.indice -> { na.x -= nx * sovrapposizione; na.y -= ny * sovrapposizione }
                    else -> {
                        na.x -= nx * sovrapposizione * 0.5f
                        na.y -= ny * sovrapposizione * 0.5f
                        nb.x += nx * sovrapposizione * 0.5f
                        nb.y += ny * sovrapposizione * 0.5f
                    }
                }
            }
        }
    }

    /** Tiene il centro dentro lo schermo, bordo e margine compresi. */
    private fun limita(n: Nodo, w: Float, h: Float) {
        val minX = n.r + PAD
        val maxX = max(minX, w - n.r - PAD)
        val minY = n.r + PAD
        val maxY = max(minY, h - n.r - PAD)
        n.x = min(maxX, max(minX, n.x))
        n.y = min(maxY, max(minY, n.y))
    }
}
