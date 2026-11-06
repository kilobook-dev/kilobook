package com.kilobook.app

import android.app.Application
import android.content.Context
import com.kilobook.app.data.KiloBookDatabase
import com.kilobook.app.recorder.RecorderSettings
import com.kilobook.app.recorder.RecordingSession

class KiloBookApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}

/** App-wide singletons. Equivalent of a small dependency container. */
class AppGraph(context: Context) {
    val db = KiloBookDatabase.create(context)
    val settings = RecorderSettings(context)
    val session = RecordingSession(context, db, settings)
}

val Context.graph: AppGraph get() = (applicationContext as KiloBookApp).graph
