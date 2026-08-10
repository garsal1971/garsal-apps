package com.garsal.appsphere.core

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * La barra blu alta 56 dp con i cerchi olimpici, uguale al `#garsal-top-bar`
 * che tutte le pagine web hanno in cima.
 */
@Composable
fun GarsalTopBar(
    titolo: String,
    modifier: Modifier = Modifier,
    onIndietro: (() -> Unit)? = null,
    azioni: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Palette.topBar)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onIndietro != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Indietro",
                tint = Palette.light,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onIndietro),
            )
        } else {
            CerchiOlimpici(Modifier.size(34.dp))
        }

        Text(
            text = titolo,
            color = Palette.light,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { azioni() }
    }
}
