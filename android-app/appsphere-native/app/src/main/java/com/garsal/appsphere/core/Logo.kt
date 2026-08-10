package com.garsal.appsphere.core

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * I cerchi olimpici del logo, gli stessi della barra in alto delle pagine web.
 * Disegnati e non importati come immagine: sono cinque cerchi, e così si
 * adattano a qualsiasi dimensione senza portarsi dietro un asset.
 */
@Composable
fun CerchiOlimpici(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 7f
        val spessore = r / 3.2f
        val cx = size.width / 2f
        val cy = size.height / 2f

        // fila superiore (blu, nero, rosso), fila inferiore (giallo, verde)
        val centri = listOf(
            Offset(cx - 2.2f * r, cy - 0.5f * r) to Palette.olimpici[0],
            Offset(cx, cy - 0.5f * r) to Palette.olimpici[2],
            Offset(cx + 2.2f * r, cy - 0.5f * r) to Palette.olimpici[4],
            Offset(cx - 1.1f * r, cy + 0.6f * r) to Palette.olimpici[1],
            Offset(cx + 1.1f * r, cy + 0.6f * r) to Palette.olimpici[3],
        )

        centri.forEach { (centro, colore) ->
            drawCircle(color = colore, radius = r, center = centro, style = Stroke(spessore))
        }
    }
}
