package com.kilobook.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilobook.app.DashboardViewModel
import com.kilobook.app.R
import com.kilobook.app.data.TripStatus
import com.kilobook.app.ui.theme.KiloSize
import com.kilobook.app.ui.theme.KiloType

enum class Tab(val label: String) {
    HOME("홈"),
    TRIPS("기록"),
    SETTINGS("설정"),
    DEBUG("디버그"),
}

/**
 * Screen split (5-1): 홈 / 기록 / 설정 for the driver, 디버그 only when 설정 › 디버그 패널 보기 is on.
 * While driving (5-7) the driver tabs collapse into the driving view; the debug tab stays for testing.
 */
@Composable
fun MainScreen(viewModel: DashboardViewModel) {
    val driving by viewModel.driving.collectAsStateWithLifecycle()
    val showDebug by viewModel.showDebugPanel.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(Tab.HOME) }
    var licensesOpen by rememberSaveable { mutableStateOf(false) }

    val tabs = buildList {
        add(Tab.HOME)
        if (!driving) {
            add(Tab.TRIPS)
            add(Tab.SETTINGS)
        }
        if (showDebug) add(Tab.DEBUG)
    }
    // A tab that is not offered right now (driving, debug switched off) falls back to 홈 without losing the choice.
    val current = if (selected in tabs) selected else Tab.HOME
    val showLicenses = current == Tab.SETTINGS && licensesOpen

    BackHandler(enabled = showLicenses) { licensesOpen = false }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(
            tabs = tabs,
            current = current,
            driving = driving,
            onSelect = {
                selected = it
                licensesOpen = false
            },
        )
        HorizontalDivider()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (current) {
                Tab.HOME -> if (driving) {
                    val trips by viewModel.trips.collectAsStateWithLifecycle()
                    val vehicle by viewModel.vehicle.collectAsStateWithLifecycle()
                    DrivingView(
                        openTrip = trips.firstOrNull { it.status == TripStatus.OPEN },
                        odometerKm = vehicle.odometerKm.value,
                    )
                } else {
                    HomeScreen(viewModel, onOpenTrips = { selected = Tab.TRIPS })
                }
                Tab.TRIPS -> TripListScreen(viewModel)
                Tab.SETTINGS -> if (showLicenses) {
                    LicensesScreen(onBack = { licensesOpen = false })
                } else {
                    SettingsScreen(viewModel, onOpenLicenses = { licensesOpen = true })
                }
                Tab.DEBUG -> DebugScreen(viewModel)
            }
        }
    }
}

@Composable
private fun TopBar(tabs: List<Tab>, current: Tab, driving: Boolean, onSelect: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = KiloSize.screenPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KiloSize.itemGap),
    ) {
        Text(stringResource(R.string.app_name), style = KiloType.title)
        Spacer(Modifier.width(KiloSize.itemGap))
        tabs.forEach { tab -> TabButton(tab.label, selected = tab == current) { onSelect(tab) } }
        Spacer(Modifier.weight(1f))
        if (driving) Text("주행 중", style = KiloType.labelStrong, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(40.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.heightIn(min = KiloSize.buttonMedium),
    ) {
        Box(Modifier.heightIn(min = KiloSize.buttonMedium).padding(horizontal = 36.dp), contentAlignment = Alignment.Center) {
            Text(label, style = KiloType.labelStrong)
        }
    }
}
