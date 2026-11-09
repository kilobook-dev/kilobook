package com.kilobook.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Text and touch sizes for the driver screens, kept in one place (Phase 5-6).
 *
 * Source: Pleos Connect design guide, checked 2026-11-09
 * (document.pleos.ai › docs-design › Foundations › Typography, Components › Basic button).
 * - Typography scale: Headline 60/56/48, Title 40/36/32, Body 30/28/26, Label 24/20.
 *   The guide gives line heights for Title (52/44/40) and Label (28/24) only; the others use about 1.2×.
 * - Minimum text: 12sp (Android accessibility, quoted by the guide). Driver screens use Label 20 as their smallest.
 * - Basic button sizes: Large 100, Medium 80, Small 64.
 * - No minimum touch-target number is published in the Pleos or Android for Cars guides; the button sizes above are used instead.
 * The guide's values are px; the Pleos Connect emulator display is 160 dpi, where 1 px = 1 dp.
 * The debug panel is a developer tool and keeps its smaller text.
 */

object KiloSize {
    /** Main driver actions: purpose buttons, 주행 종료. */
    val buttonLarge = 100.dp
    /** Secondary actions: tabs, list actions, settings rows. */
    val buttonMedium = 80.dp
    val buttonSmall = 64.dp

    val screenPadding = 32.dp
    val sectionGap = 24.dp
    val itemGap = 16.dp
    val cardPadding = 28.dp
}

object KiloType {
    val headlineLarge = TextStyle(fontSize = 60.sp, lineHeight = 72.sp, fontWeight = FontWeight.ExtraBold)
    val headline = TextStyle(fontSize = 48.sp, lineHeight = 58.sp, fontWeight = FontWeight.Bold)
    val titleLarge = TextStyle(fontSize = 40.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold)
    val title = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
    val body = TextStyle(fontSize = 26.sp, lineHeight = 34.sp)
    val bodyStrong = body.copy(fontWeight = FontWeight.Bold)
    val label = TextStyle(fontSize = 24.sp, lineHeight = 28.sp)
    val labelStrong = label.copy(fontWeight = FontWeight.Bold)
    val labelSmall = TextStyle(fontSize = 20.sp, lineHeight = 24.sp)
}

@Composable
fun KiloTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        typography = Typography(
            bodyLarge = KiloType.body,
            bodyMedium = KiloType.label,
            labelLarge = KiloType.labelStrong,
        ),
        content = content,
    )
}
