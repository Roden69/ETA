package com.example.eta.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.domain.setup.SuggestedFeature
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.theme.EtaTheme

/**
 * The dashboard's offer of one optional feature, dismissable in three ways.
 *
 * Shown above the day's content, but never auto-enabling anything: the feature is
 * switched on only by "Ausprobieren".
 */
@Composable
fun FeatureSuggestionCard(
    viewModel: FeatureSuggestionViewModel,
    modifier: Modifier = Modifier,
) {
    val offer by viewModel.suggestion.collectAsStateWithLifecycle()
    val enabled by viewModel.justEnabled.collectAsStateWithLifecycle()
    val switchedOn = enabled
    if (switchedOn != null) {
        EnabledCard(
            feature = switchedOn,
            onTutorial = { viewModel.openTutorial(switchedOn) },
            onClose = viewModel::closeEnabled,
            modifier = modifier,
        )
    } else {
        offer?.let { feature ->
            OfferCard(
                feature = feature,
                onTry = { viewModel.tryFeature(feature) },
                onLater = viewModel::postpone,
                onNever = { viewModel.neverSuggest(feature) },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun OfferCard(
    feature: SuggestedFeature,
    onTry: () -> Unit,
    onLater: () -> Unit,
    onNever: () -> Unit,
    modifier: Modifier,
) {
    EtaSurface(
        modifier = modifier.fillMaxWidth(),
        borderColor = EtaTheme.colors.accent,
        color = EtaTheme.colors.surfaceRaised,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaText(
                text = "Vorschlag · freiwillig",
                style = EtaTheme.typography.label,
                color = EtaTheme.colors.accent,
            )
            EtaText(
                text = "Möchtest du ${feature.label} ausprobieren?",
                style = EtaTheme.typography.heading,
            )
            EtaText(
                text = feature.summary,
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textSecondary,
            )
            EtaText(
                text = "Eingeschaltet wird nur, wenn du »Ausprobieren« tippst.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                EtaButton(text = "Ausprobieren", onClick = onTry)
                EtaButton(text = "Später", style = EtaButtonStyle.Secondary, onClick = onLater)
            }
            EtaButton(
                text = "Nicht mehr vorschlagen",
                style = EtaButtonStyle.Secondary,
                onClick = onNever,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EnabledCard(
    feature: SuggestedFeature,
    onTutorial: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val tutorial = FeatureSuggestionViewModel.tutorialOf(feature)
    EtaSurface(
        modifier = modifier.fillMaxWidth(),
        borderColor = EtaTheme.colors.accent,
        color = EtaTheme.colors.surfaceRaised,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaText(
                text = "${feature.label} ist eingeschaltet.",
                style = EtaTheme.typography.heading,
            )
            if (tutorial != null) {
                EtaText(
                    text = "Ein kurzes Tutorial zeigt dir, wie es funktioniert.",
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    EtaButton(text = "Tutorial ansehen", onClick = onTutorial)
                    EtaButton(text = "Nicht jetzt", style = EtaButtonStyle.Secondary, onClick = onClose)
                }
            } else {
                EtaText(
                    text = "Du findest ${feature.label} jetzt in der Tableiste.",
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.textSecondary,
                )
                EtaButton(text = "Schließen", style = EtaButtonStyle.Secondary, onClick = onClose)
            }
        }
    }
}
