package com.kilobook.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kilobook.app.ui.theme.KiloSize
import com.kilobook.app.ui.theme.KiloType

/** A rounded block for one group of information on the driver screens. */
@Composable
fun KiloCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = color, shape = RoundedCornerShape(20.dp), modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(KiloSize.cardPadding),
            verticalArrangement = Arrangement.spacedBy(KiloSize.itemGap),
            content = content,
        )
    }
}

/** Filled button with a fixed touch height (Pleos basic button sizes). */
@Composable
fun BigButton(
    label: String,
    modifier: Modifier = Modifier,
    height: Dp = KiloSize.buttonLarge,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = height)) {
        Text(label, style = if (height >= KiloSize.buttonLarge) KiloType.title else KiloType.labelStrong, textAlign = TextAlign.Center)
    }
}

@Composable
fun BigOutlinedButton(
    label: String,
    modifier: Modifier = Modifier,
    height: Dp = KiloSize.buttonLarge,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = height)) {
        Text(label, style = if (height >= KiloSize.buttonLarge) KiloType.title else KiloType.labelStrong, textAlign = TextAlign.Center)
    }
}

/** Small tag such as "예시" or "기록되지 않은 주행". */
@Composable
fun Tag(text: String, color: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Surface(color = color, shape = RoundedCornerShape(8.dp)) {
        Text(text, style = KiloType.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KiloSize.itemGap)) {
        Text(text, style = KiloType.title)
        trailing()
    }
}

/** Secondary text color for explanations under values. */
@Composable
fun mutedColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant
