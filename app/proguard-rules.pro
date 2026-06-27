# Token Addict — ProGuard / R8 keep rules

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
-keep class com.tokenaddict.app.data.KimiProvider { *; }
-keep class com.tokenaddict.app.data.KimiTokenManager { *; }
-keep class com.tokenaddict.app.data.SessionManager { *; }
-keep class com.tokenaddict.app.data.NotificationScheduler { *; }
-keep class com.tokenaddict.app.data.NotificationMessageProvider { *; }
-keep class com.tokenaddict.app.data.WebViewCookieJar { *; }

# ── Gson inner data classes used for deserialization ─────────────────────────
# KimiOAuthManager inner classes use @SerializedName but live outside model.**
-keep class com.tokenaddict.app.data.KimiOAuthManager$DeviceCodeResponse { *; }
-keep class com.tokenaddict.app.data.KimiOAuthManager$TokenResponse { *; }

# ── Gson reflection metadata ────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Gson TypeToken subclasses (anonymous object : TypeToken<...>() {}) must keep
# their generic Signature attribute or Gson throws IllegalStateException at runtime.
# In R8 full mode, -keepattributes only applies to explicitly kept classes.
-keep class * extends com.google.gson.reflect.TypeToken { *; }

# ── Kotlin metadata for sealed classes and coroutines ────────────────────────
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations

# ── security-crypto (EncryptedSharedPreferences + Tink internals) ────────────
# security-crypto:1.1.0-alpha06 bundles NO consumer ProGuard rules.
# Without these, R8 strips internal Tink crypto classes that EncryptedSharedPreferences
# instantiates via reflection at runtime — causing a crash on every cold start.
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

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

