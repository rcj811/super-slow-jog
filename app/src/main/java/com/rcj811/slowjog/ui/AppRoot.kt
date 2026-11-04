package com.rcj811.slowjog.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rcj811.slowjog.Phase
import com.rcj811.slowjog.RunRepository

@Composable
fun AppRoot(onStart: () -> Unit) {
    val run by RunRepository.state.collectAsStateWithLifecycle()
    val inRun = run.phase != Phase.IDLE

    AnimatedContent(
        targetState = inRun,
        transitionSpec = {
            (fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 12 }) togetherWith fadeOut(tween(250))
        },
        label = "root",
        modifier = Modifier.fillMaxSize().background(JogColors.Bg),
    ) { running ->
        if (running) RunScreen(RunRepository.state.collectAsStateWithLifecycle().value) else Tabs(onStart)
    }
}

@Composable
private fun Tabs(onStart: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val items = listOf("跑步" to Glyph.RUN, "統計" to Glyph.CHART, "設定" to Glyph.GEAR)
    Scaffold(
        containerColor = JogColors.Bg,
        bottomBar = {
            NavigationBar(containerColor = JogColors.Surface, tonalElevation = 0.dp) {
                items.forEachIndexed { i, (label, glyph) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { GlyphIcon(glyph, if (tab == i) JogColors.Sun1 else JogColors.Muted, Modifier.size(24.dp)) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = JogColors.Sun1,
                            unselectedTextColor = JogColors.Muted,
                            indicatorColor = JogColors.Sun2.copy(alpha = 0.16f),
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            AnimatedContent(targetState = tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "tab") { t ->
                Column(Modifier.fillMaxSize()) {
                    when (t) {
                        0 -> SetupScreen(onStart)
                        1 -> StatsScreen()
                        else -> SettingsScreen()
                    }
                }
            }
        }
    }
}
