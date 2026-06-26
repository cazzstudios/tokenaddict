# Token Addict — ProGuard / R8 keep rules
# These rules are prepared for when minification is enabled in Task 13.

# ── Gson model classes (serialized/deserialized via reflection) ──────────────
-keep class com.tokenaddict.app.data.model.** { *; }

# ── Application class (instantiated by the framework) ───────────────────────
-keep class com.tokenaddict.app.TokenAddictApplication { *; }

# ── WorkManager workers (referenced by class name from manifest/work requests) ──
-keep class com.tokenaddict.app.worker.ClaudeUsageWorker { *; }
-keep class com.tokenaddict.app.worker.KimiUsageWorker { *; }

# ── Broadcast receivers declared in AndroidManifest.xml ─────────────────────
-keep class com.tokenaddict.app.receiver.ResetAlarmReceiver { *; }
-keep class com.tokenaddict.app.receiver.BootReceiver { *; }

# ── Activities declared in AndroidManifest.xml ──────────────────────────────
-keep class com.tokenaddict.app.ui.MainActivity { *; }
-keep class com.tokenaddict.app.ui.LoginActivity { *; }
-keep class com.tokenaddict.app.ui.KimiLoginActivity { *; }
-keep class com.tokenaddict.app.ui.SettingsActivity { *; }

# ── AiProvider interface and implementations (used via type lookups) ────────
-keep interface com.tokenaddict.app.data.AiProvider { *; }
-keep class com.tokenaddict.app.data.ClaudeProvider { *; }
-keep class com.tokenaddict.app.data.KimiOAuthManager { *; }

# ── Gson reflection metadata ────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── Kotlin metadata for sealed classes and coroutines ────────────────────────
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations

# ── EncryptedSharedPreferences (security-crypto) direct entry points ────────
# Consumer rules are bundled, but we keep the classes we instantiate directly.
-keep class androidx.security.crypto.EncryptedSharedPreferences { *; }
-keep class androidx.security.crypto.MasterKey { *; }
-keep class androidx.security.crypto.MasterKey$Builder { *; }

# ── Secure storage abstraction (T1/T2) ─────────────────────────────────────
-keep class com.tokenaddict.app.data.SecurePreferences { *; }
-keep class com.tokenaddict.app.data.SecurePreferences$Companion { *; }
-keep class com.tokenaddict.app.data.SecureStorageException { *; }

# ── Security checker (T2) ──────────────────────────────────────────────────
-keep class com.tokenaddict.app.security.SecurityChecker { *; }
-keep class com.tokenaddict.app.security.SecurityStatus { *; }
-keep class com.tokenaddict.app.security.SecurityStatus$Safe { *; }
-keep class com.tokenaddict.app.security.SecurityStatus$Risky { *; }
-keep class com.tokenaddict.app.security.SecurityEnvironment { *; }
-keep class com.tokenaddict.app.security.DefaultSecurityEnvironment { *; }

