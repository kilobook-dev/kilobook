package com.kilobook.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilobook.app.DashboardViewModel
import com.kilobook.app.R
import com.kilobook.app.ui.theme.KiloSize
import com.kilobook.app.ui.theme.KiloType

/** 5-1 settings: the debug panel switch (saved), open-source licenses (5-10), app version. */
@Composable
fun SettingsScreen(viewModel: DashboardViewModel, onOpenLicenses: () -> Unit) {
    val showDebug by viewModel.showDebugPanel.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(KiloSize.screenPadding),
        verticalArrangement = Arrangement.spacedBy(KiloSize.itemGap),
    ) {
        SectionTitle("설정")

        SettingRow(
            title = "디버그 패널 보기",
            detail = "차량 API 결과, 기록 상태, 디버그 로그를 보여 주는 '디버그' 탭을 켭니다.",
            modifier = Modifier.toggleable(value = showDebug, role = Role.Switch, onValueChange = viewModel::setShowDebugPanel),
        ) {
            // The whole row is the touch target; the switch only shows the state, drawn larger to be readable.
            Switch(checked = showDebug, onCheckedChange = null, modifier = Modifier.padding(horizontal = 12.dp).scale(1.5f))
        }

        SettingRow(
            title = "오픈소스 라이선스",
            detail = "이 앱이 사용하는 오픈소스 라이브러리와 라이선스 전문",
            onClick = onOpenLicenses,
        ) {
            Text("보기 ›", style = KiloType.labelStrong, color = MaterialTheme.colorScheme.primary)
        }

        SettingRow(title = "앱 정보", detail = "${context.getString(R.string.app_name)} $version") {}
    }
}

@Composable
private fun SettingRow(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val content: @Composable () -> Unit = {
        Row(
            Modifier.padding(horizontal = KiloSize.cardPadding, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KiloSize.sectionGap),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = KiloType.bodyStrong)
                Text(detail, style = KiloType.label, color = mutedColor())
            }
            trailing()
        }
    }
    val rowModifier = modifier.fillMaxWidth().heightIn(min = KiloSize.buttonLarge)
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = rowModifier) { content() }
    } else {
        Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = rowModifier) { content() }
    }
}
