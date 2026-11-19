package com.example.core

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        if (FirebaseApp.getApps(context).isEmpty()) {
            Log.w(TAG, "Skipping App Check setup: FirebaseApp is not configured in this build.")
            return
        }

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val prefs = context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )

            if (!debugToken.isNullOrEmpty()) {
                // Synchronous commit() is required before DebugAppCheckProviderFactory initializes.
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            }

            val hasDebugSecret = !prefs.getString(DEBUG_SECRET_KEY, null).isNullOrEmpty()
            if (hasDebugSecret) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "DebugAppCheckProviderFactory installed.")
            } else {
                // On physical devices (e.g. GitHub Actions APK) without an injected debug token,
                // prefer reCAPTCHA Enterprise using the provisioned site key.
                val installedRecaptcha = installRecaptchaProvider(context)
                if (!installedRecaptcha) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                    )
                    Log.w(TAG, "Fell back to DebugAppCheckProviderFactory without debug token.")
                }
            }
        } else {
            installRecaptchaProvider(context)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

private fun installRecaptchaProvider(context: Context): Boolean {
    val resId = context.resources.getIdentifier(
        RECAPTCHA_SITE_KEY_RES,
        "string",
        context.packageName
    )
    if (resId == 0) {
        Log.e(TAG, "recaptcha_site_key string resource not found in package.")
        return false
    }
    val siteKey = context.getString(resId)
    if (siteKey.isBlank()) {
        Log.e(TAG, "recaptcha_site_key resource string is blank.")
        return false
    }
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
    )
    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
    return true
}
