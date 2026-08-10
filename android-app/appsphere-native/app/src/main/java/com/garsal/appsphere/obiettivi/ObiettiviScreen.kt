package com.garsal.appsphere.obiettivi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.garsal.appsphere.core.GarsalTopBar
import com.garsal.appsphere.core.Palette

@Composable
fun ObiettiviScreen(onIndietro: () -> Unit) {
    Scaffold(
        topBar = { GarsalTopBar(titolo = "Obiettivi", onIndietro = onIndietro) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("In arrivo.", color = Palette.muted)
        }
    }
}
