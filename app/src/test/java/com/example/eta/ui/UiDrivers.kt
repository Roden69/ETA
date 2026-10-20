package com.example.eta.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

/*
 * Compose helpers for the UI tests: they find what a user would see by its German
 * text, and wait for the ViewModels' coroutines to show it. Nothing here knows a
 * rule of the app.
 */

/** How long a screen may take to show what the data it reads says, Room included. */
private const val SHOW_TIMEOUT_MILLIS = 10_000L

/** Waits until [text] is on screen; fails with the text when it never appears. */
fun ComposeTestRule.awaitText(text: String) {
    waitUntil(timeoutMillis = SHOW_TIMEOUT_MILLIS) {
        isShown(text)
    }
}

/** Waits until [text] is no longer on screen. */
fun ComposeTestRule.awaitGone(text: String) {
    waitUntil(timeoutMillis = SHOW_TIMEOUT_MILLIS) {
        !isShown(text)
    }
}

/** Whether a node with exactly [text] is in the tree right now. */
fun ComposeTestRule.isShown(text: String): Boolean =
    onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

/** Scrolls to the node with exactly [text] when it sits in a scrolling column, then taps it. */
fun ComposeTestRule.tap(text: String) {
    awaitText(text)
    node(text).performScrollTo().performClick()
}

/** The node with exactly [text]; fails when there is not exactly one. */
fun ComposeTestRule.node(text: String): SemanticsNodeInteraction = onNodeWithText(text)
