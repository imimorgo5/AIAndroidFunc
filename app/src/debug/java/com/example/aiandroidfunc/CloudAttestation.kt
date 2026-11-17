package com.example.aiandroidfunc

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/** Register the token from Logcat in Firebase Console before cloud inference. */
object CloudAttestation {
    fun install(app: FirebaseApp) {
        FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    }
}
