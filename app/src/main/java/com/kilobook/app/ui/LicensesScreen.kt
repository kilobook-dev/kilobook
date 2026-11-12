package com.kilobook.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.kilobook.app.ui.theme.KiloSize
import com.kilobook.app.ui.theme.KiloType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 5-10: the display list from docs/open-source-licenses.md and one full copy of the Apache License 2.0
 * (Apache 2.0 section 4(a): give recipients a copy of the license). Update both when libraries change.
 */
private data class License(val name: String, val version: String, val license: String, val url: String)

private val LICENSES = listOf(
    License(
        "AndroidX (Activity, Annotation, AppCompat, Arch Core, Autofill, CardView, Collection, Concurrent, ConstraintLayout, " +
            "CoordinatorLayout, Core, CursorAdapter, CustomView, DocumentFile, DrawerLayout, DynamicAnimation, Emoji2, Fragment, " +
            "Graphics, Interpolator, Legacy Support, Lifecycle, Loader, LocalBroadcastManager, Print, ProfileInstaller, " +
            "RecyclerView, Resource Inspection, Room, SavedState, Security, SQLite, Startup, Tracing, Transition, VectorDrawable, " +
            "VersionedParcelable, ViewPager, ViewPager2)",
        "라이브러리별",
        APACHE,
        "https://developer.android.com/jetpack/androidx",
    ),
    License(
        "Jetpack Compose (Animation, Foundation, Runtime, UI, Material, Material 3)",
        "1.7.8 · Material 1.6.6 · Material 3 1.2.1",
        APACHE,
        "https://developer.android.com/jetpack/compose",
    ),
    License("Kotlin Standard Library, Kotlin Reflect", "2.0.21 · 1.9.25", APACHE, "https://kotlinlang.org/"),
    License("kotlinx.coroutines", "1.7.3", APACHE, "https://github.com/Kotlin/kotlinx.coroutines"),
    License("kotlinx.serialization", "1.7.3", APACHE, "https://github.com/Kotlin/kotlinx.serialization"),
    License("JetBrains Java Annotations", "23.0.0", APACHE, "https://github.com/JetBrains/java-annotations"),
    License("Dagger, Hilt", "2.51.1", APACHE, "https://github.com/google/dagger"),
    License("Gson", "2.10.1", APACHE, "https://github.com/google/gson"),
    License("Guava ListenableFuture", "1.0", APACHE, "https://github.com/google/guava"),
    License("JSR 305 Annotations", "3.0.2", APACHE, "http://findbugs.sourceforge.net/"),
    License("javax.inject", "1", APACHE, "http://code.google.com/p/atinject/"),
    License("JSpecify", "1.0.0", APACHE, "https://jspecify.org/"),
    License("Material Components for Android", "1.10.0", APACHE, "https://github.com/material-components/material-components-android"),
    License("Tink", "1.8.0", APACHE, "https://github.com/tink-crypto/tink-java"),
    License("Error Prone Annotations", "2.15.0", APACHE, "https://github.com/google/error-prone"),
    License(
        "JNA (Java Native Access)",
        "5.13.0",
        "Apache License 2.0 (LGPL-2.1-or-later와 Apache 2.0 중 Apache 2.0을 선택)",
        "https://github.com/java-native-access/jna",
    ),
)

private const val PLEOS_TERMS = "Pleos Playground 이용약관"
private const val PLEOS_TERMS_URL =
    "https://developers.pleos.ai/playground/external/use-policy?partnerId=672505ad-2e37-4500-8162-000bcfc05b5c&locale=en-US"

/** Pleos SDKs: not open source, listed for completeness. */
private val PLEOS = listOf(
    License("Pleos Vehicle SDK (오픈소스 아님)", "2.3.2", PLEOS_TERMS, PLEOS_TERMS_URL),
    License("Pleos TextToSpeech · SpeechToText SDK (오픈소스 아님)", "3.3.1", PLEOS_TERMS, PLEOS_TERMS_URL),
    License("Pleos Universal Access (오픈소스 아님, 음성 SDK가 함께 가져옴)", "0.6.0", PLEOS_TERMS, PLEOS_TERMS_URL),
)

private const val APACHE = "Apache License 2.0"
private const val LICENSE_ASSET = "licenses/LICENSE-2.0.txt"

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val apacheText by produceState<List<String>?>(null) {
        value = withContext(Dispatchers.IO) {
            context.assets.open(LICENSE_ASSET).bufferedReader().use { it.readText() }
                .split(Regex("\\n\\s*\\n"))
                .map { it.trimEnd() }
                .filter { it.isNotBlank() }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(KiloSize.screenPadding),
        verticalArrangement = Arrangement.spacedBy(KiloSize.itemGap),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(KiloSize.sectionGap), verticalAlignment = Alignment.CenterVertically) {
                BigOutlinedButton("‹ 설정", height = KiloSize.buttonMedium, onClick = onBack)
                Text("오픈소스 라이선스", style = KiloType.title)
            }
        }
        item {
            Text(
                "킬로북은 아래 오픈소스 라이브러리를 사용합니다. 모두 Apache License 2.0으로 사용하며, 전문은 이 화면 아래에 있습니다.",
                style = KiloType.body,
            )
        }
        items(LICENSES) { LicenseItem(it) }
        items(PLEOS) { LicenseItem(it) }
        item { Text("Apache License 2.0 전문", style = KiloType.title, modifier = Modifier.padding(top = KiloSize.sectionGap)) }
        val paragraphs = apacheText
        if (paragraphs == null) {
            item { Text("불러오는 중…", style = KiloType.label) }
        } else {
            items(paragraphs) { paragraph ->
                SelectionContainer {
                    Text(paragraph, style = KiloType.labelSmall.copy(fontFamily = FontFamily.Monospace))
                }
            }
        }
    }
}

@Composable
private fun LicenseItem(license: License) {
    KiloCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(license.name, style = KiloType.bodyStrong)
            Text("${license.version} · ${license.license}", style = KiloType.label)
            Text(license.url, style = KiloType.labelSmall, color = mutedColor())
        }
    }
}
