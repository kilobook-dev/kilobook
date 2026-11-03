package com.odolog.spike

import android.app.Application
import android.content.Context
import com.odolog.spike.data.OdologDatabase
import com.odolog.spike.recorder.RecorderSettings
import com.odolog.spike.recorder.RecordingSession

class OdologApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}

/** App-wide singletons. Equivalent of a small dependency container. */
class AppGraph(context: Context) {
    val db = OdologDatabase.create(context)
    val settings = RecorderSettings(context)
    val session = RecordingSession(context, db, settings)
}

val Context.graph: AppGraph get() = (applicationContext as OdologApp).graph
