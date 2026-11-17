package com.example.aiandroidfunc

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

object CloudAttestation {
    fun install(app: FirebaseApp) {
        FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
    }
}
