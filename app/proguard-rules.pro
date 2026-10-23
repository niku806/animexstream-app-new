# ---------- Obfuscation ----------
-repackageclasses 'a'
-allowaccessmodification
-overloadaggressively
-useuniqueclassmembernames

# ---------- Kotlin ----------
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ---------- Compose ----------
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-dontwarn androidx.compose.**

-keepclasseswithmembernames class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ---------- Material icons ----------
-keep class androidx.compose.material.icons.** { *; }

# ---------- OkHttp ----------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------- Coil ----------
-keep class coil.** { *; }
-dontwarn coil.**

# ---------- Media3 / ExoPlayer ----------
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ---------- org.json ----------
-keep class org.json.** { *; }

# ---------- Firebase Realtime Database (reflection heavy) ----------
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ---------- Keep data models (used in reflection-ish flows) ----------
-keep class com.animexstream.app.data.** { *; }

# ---------- Secrets: MUST be kept for runtime logic, but obfuscated ----------
-keep class com.animexstream.app.Secrets { *; }

# ---------- Keep Android view classes referenced from Compose ----------
-keep class * extends android.view.View { *; }
