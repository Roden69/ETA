package com.example.eta.ui.attributes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaTextField
import com.example.eta.ui.theme.EtaTheme

/**
 * The steps that come after a ToDo, in order.
 *
 * Each one waits for the one before it. Empty names are dropped when the card is
 * saved, so a freshly added row costs nothing if it is left blank.
 */
@Composable
fun FollowUpsField(
    names: List<String>,
    onNames: (List<String>) -> Unit,
) {
    EtaField(
        label = "Danach",
        hint = "Folgeaufgaben in dieser Reihenfolge. Jede wartet, bis die davor erledigt " +
            "ist; zwischen den Schritten darf beliebig viel Zeit liegen.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            names.forEachIndexed { index, name ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EtaTextField(
                        value = name,
                        onValueChange = { typed ->
                            onNames(names.mapIndexed { i, old -> if (i == index) typed else old })
                        },
                        placeholder = "${index + 1}. Folgeaufgabe",
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.size(EtaTheme.spacing.sm))
                    EtaButton(
                        text = "Entfernen",
                        style = EtaButtonStyle.Secondary,
                        onClick = { onNames(names.filterIndexed { i, _ -> i != index }) },
                    )
                }
            }
            EtaButton(
                text = "Folgeaufgabe hinzufügen",
                style = EtaButtonStyle.Secondary,
                onClick = { onNames(names + "") },
            )
        }
    }
}
