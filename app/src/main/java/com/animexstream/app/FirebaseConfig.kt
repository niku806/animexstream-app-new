package com.animexstream.app

import android.content.Context
import com.animexstream.app.data.UserPrefs
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.FirebaseDatabase

object FirebaseConfig {

    @Volatile private var initialized = false

    fun init(context: Context) {
        UserPrefs.init(context)
        if (initialized || FirebaseApp.getApps(context).isNotEmpty()) {
            initialized = true
            return
        }
        val options = FirebaseOptions.Builder()
            .setApiKey(Secrets.RTDB_API_KEY)
            .setApplicationId(Secrets.RTDB_APP_ID)
            .setProjectId(Secrets.RTDB_PROJECT_ID)
            .setDatabaseUrl(Secrets.RTDB_URL)
            .build()
        FirebaseApp.initializeApp(context, options)
        initialized = true
    }

    fun database(): FirebaseDatabase = FirebaseDatabase.getInstance()
}
