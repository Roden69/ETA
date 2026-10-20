package com.example.eta.ui.weekplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.eta.ui.attributes.TodoAttributeFields
import com.example.eta.ui.attributes.TodoAttributes
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTextField
import com.example.eta.ui.subtasks.FoldCandidate
import com.example.eta.ui.theme.EtaTheme
import kotlinx.datetime.LocalDate

/**
 * The ToDo form of the weekly planning, for a goal made here and for a card that is
 * still missing its answers.
 *
 * Both finish a card the same way: `TodoAttributeFields` is the concretizing step's
 * own form, so the questions are literally the same ones. A goal made here is created
 * **already concretized** — a bare note pulled into the week could never be planned.
 */
@Composable
fun GoalDialog(
    today: LocalDate,
    title: String,
    hint: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, attributes: TodoAttributes) -> Unit,
    initialName: String = "",
    initialAttributes: TodoAttributes = TodoAttributes(),
    /** Whether the form offers Folgeaufgaben; a card that already exists has its own chain. */
    offerFollowUps: Boolean = false,
    /** ToDos the card can swallow as steps. */
    foldCandidates: List<FoldCandidate> = emptyList(),
) {
    var name by remember { mutableStateOf(initialName) }
    var attributes by remember { mutableStateOf(initialAttributes) }

    Dialog(onDismissRequest = onDismiss) {
        EtaSurface(
            modifier = Modifier.widthIn(max = 400.dp),
            color = EtaTheme.colors.surfaceRaised,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 600.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
            ) {
                EtaText(text = title, style = EtaTheme.typography.title)
                EtaText(
                    text = hint,
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )

                EtaField(label = "Name") {
                    EtaTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Was steht an?",
                    )
                }

                TodoAttributeFields(
                    value = attributes,
                    onChange = { attributes = it },
                    today = today,
                    offerFollowUps = offerFollowUps,
                    groupName = name,
                    onGroupName = { name = it },
                    foldCandidates = foldCandidates,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    EtaButton(
                        text = "Abbrechen",
                        style = EtaButtonStyle.Secondary,
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.weight(1f))
                    EtaButton(
                        text = confirmLabel,
                        enabled = name.isNotBlank(),
                        onClick = { onConfirm(name.trim(), attributes) },
                    )
                }
            }
        }
    }
}
