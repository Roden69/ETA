package com.example.eta.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.ItemType
import androidx.compose.ui.graphics.Color

private val LocalEtaColors = staticCompositionLocalOf<EtaColors> {
    error("No EtaColors provided — wrap the content in EtaTheme.")
}
private val LocalEtaTypography = staticCompositionLocalOf { DefaultEtaTypography }
private val LocalEtaShapes = staticCompositionLocalOf { EtaShapes() }
private val LocalEtaSpacing = staticCompositionLocalOf { EtaSpacing() }

/**
 * The app's design system, built on Compose Foundation only.
 *
 * There is deliberately no Material dependency: the revolver, the drag-and-drop
 * timeline and the card shapes that encode where a block came from all need full
 * control over gesture and paint, which Material components would fight.
 *
 * [choice] picks the tokens; every activity passes the one in `DesignStore`.
 */
@Composable
fun EtaTheme(
    choice: DesignChoice,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalEtaColors provides choice.colors(systemDark = isSystemInDarkTheme()),
        LocalEtaTypography provides DefaultEtaTypography,
        LocalEtaShapes provides shapesOf(choice.design),
        LocalEtaSpacing provides EtaSpacing(),
        content = content,
    )
}

/** Access point for the tokens, mirroring how MaterialTheme is normally read. */
object EtaTheme {
    val colors: EtaColors
        @Composable @ReadOnlyComposable get() = LocalEtaColors.current

    val typography: EtaTypography
        @Composable @ReadOnlyComposable get() = LocalEtaTypography.current

    val shapes: EtaShapes
        @Composable @ReadOnlyComposable get() = LocalEtaShapes.current

    val spacing: EtaSpacing
        @Composable @ReadOnlyComposable get() = LocalEtaSpacing.current
}

/**
 * The accent a task carries, so category colouring never gets hand-mapped per screen.
 *
 * Its own colour first, then its category's, and for a **standing task without
 * either** a colour of its own ([LocalRoutineColors], spread over the whole
 * standing schedule): the routines the setup lays down (Morgenroutine, Arbeiten, …)
 * have no category, and drawn in the muted grey of "unsorted" they were one
 * indistinguishable grey in every picture of the week. Anything else without a
 * category — a note not yet given its form — stays muted.
 */
@Composable
@ReadOnlyComposable
fun colorOf(item: Item): Color = item.color?.let { Color(it) }
    ?: if (item.isNameColoured()) {
        Color(LocalRoutineColors.current[item.normalizedName] ?: routineColorArgb(item.normalizedName))
    } else {
        colorOf(item.category)
    }

/** Whether [colorOf] derives this card's colour from its name instead of a category. */
fun Item.isNameColoured(): Boolean =
    color == null && category == null && type == ItemType.RECURRING && role != ItemRole.FREE_TIME

/**
 * The colour of each standing task that has none, by normalized name. Provided once
 * at the root from the whole standing schedule, so the routines of one week are told
 * apart; a name missing from it (the tutorial's practice data) falls back to
 * [routineColorArgb].
 */
val LocalRoutineColors = staticCompositionLocalOf<Map<String, Int>> { emptyMap() }

/**
 * Colours for standing tasks that have none: mid-tone, so they read on the light and
 * on the dark palette, and chosen to stay clear of the three category colours
 * (indigo, cyan, green), the calendar's ochre and the warning red.
 */
val ROUTINE_COLORS: List<Int> = listOf(
    0xFFE8833A.toInt(), // orange
    0xFFD6609B.toInt(), // pink
    0xFFA66BD6.toInt(), // purple
    0xFF8DAA2B.toInt(), // olive
    0xFFB8704F.toInt(), // terracotta
    0xFF6FA0C8.toInt(), // steel blue
    0xFFC7C73A.toInt(), // lime
    0xFFE0605A.toInt(), // coral
)

/**
 * The colour a standing task without category falls back to, from its normalized
 * name alone: `String.hashCode` is defined by the language, so it does not change
 * between runs or devices. [assignRoutineColors] is the one that keeps neighbours apart.
 */
fun routineColorArgb(normalizedName: String): Int =
    ROUTINE_COLORS[Math.floorMod(normalizedName.hashCode(), ROUTINE_COLORS.size)]

/**
 * One colour per name, distinct while the palette lasts. Each name starts at the
 * slot its hash points to and moves on to the next free one, taking the names in
 * alphabetical order — so the same set of routines always looks the same, on every
 * screen and every weekday, and two routines of one week do not share a colour until
 * there are more of them than colours.
 */
fun assignRoutineColors(normalizedNames: Collection<String>): Map<String, Int> {
    val taken = mutableSetOf<Int>()
    return normalizedNames.toSortedSet().associateWith { name ->
        val start = Math.floorMod(name.hashCode(), ROUTINE_COLORS.size)
        val slot = (0 until ROUTINE_COLORS.size).map { (start + it) % ROUTINE_COLORS.size }
            .firstOrNull { it !in taken } ?: start
        taken += slot
        ROUTINE_COLORS[slot]
    }
}

/**
 * The colour of a category. For a **card**, ask [colorOf] with the item instead:
 * a task may carry a colour of its own, and a screen that asks by category
 * would not see it.
 */
@Composable
@ReadOnlyComposable
fun colorOf(category: Category?): Color = when (category) {
    Category.FOKUS -> EtaTheme.colors.fokus
    Category.NEBENBEI -> EtaTheme.colors.nebenbei
    Category.ACHTSAM -> EtaTheme.colors.achtsam
    null -> EtaTheme.colors.textMuted
}
