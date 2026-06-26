package com.tokenaddict.app

import android.app.Application

/**
 * Test-only Application class used by Robolectric.
 *
 * Replaces [TokenAddictApplication] so that `onCreate()` does NOT
 * schedule WorkManager jobs or create notification channels, which
 * would cause `IllegalStateException` when the WorkManager singleton
 * is shared across multiple test classes.
 */
class TestTokenAddictApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Intentionally empty — no WorkManager, no notification channels.
    }
}
