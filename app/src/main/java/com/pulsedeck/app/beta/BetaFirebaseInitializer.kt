package com.pulsedeck.app.beta

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

object BetaFirebaseInitializer {
    private const val TAG = "PulseDeckBeta"

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            runCatching {
                val app = if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(context)
                } else {
                    FirebaseApp.getInstance()
                }
                if (app == null) {
                    Log.w(TAG, "Firebase config missing; beta activation will require Firebase setup.")
                    initialized = true
                    return
                }
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance(),
                )
                initialized = true
            }.onFailure { error ->
                Log.w(TAG, "Firebase/App Check initialization failed cleanly: ${error.javaClass.simpleName}")
                initialized = true
            }
        }
    }

    fun isFirebaseAvailable(context: Context): Boolean =
        runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
}
