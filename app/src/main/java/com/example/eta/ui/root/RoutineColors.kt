package com.example.eta.ui.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.di.AppContainer
import com.example.eta.ui.theme.LocalRoutineColors
import com.example.eta.ui.theme.assignRoutineColors
import com.example.eta.ui.theme.isNameColoured

/**
 * Hands the colour of every standing task without category down to the screens.
 *
 * Read from the whole standing schedule rather than per screen, so a routine has the
 * same colour in the Wochenschema, its list, the week tab and the day planner, and
 * two routines of one week are not painted alike (#51).
 */
@Composable
fun ProvideRoutineColors(container: AppContainer, content: @Composable () -> Unit) {
    val definitions by container.itemRepository.observeRecurringDefinitions()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val colors = remember(definitions) {
        assignRoutineColors(definitions.filter { it.isNameColoured() }.map { it.normalizedName })
    }
    CompositionLocalProvider(LocalRoutineColors provides colors, content = content)
}
