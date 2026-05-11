# Add project specific ProGuard rules here.
# v1.20.0 — Hardening: kompletne reguły dla release build.

# === KEEP ATTRIBUTES ===
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes SourceFile,LineNumberTable

# === ROOM ===
# Entities (kolumny wymagane dla SQL queries z @Query)
-keep class pl.filebit.gymtracker.data.entity.** { *; }
# DAO interfejsy (Room generuje implementacje, ale nazwy metod używane w Hilt providers)
-keep class pl.filebit.gymtracker.data.db.dao.** { *; }
# Room runtime
-keep class androidx.room.RoomDatabase { *; }

# === HILT / DAGGER ===
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep class **_HiltComponents$* { *; }
-keep class **_HiltModules$* { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent
-keep class * extends dagger.hilt.android.internal.managers.ApplicationComponentManager

# === COMPOSE ===
# Compose runtime używa reflection dla recompose; LiveLiterals
-keep class androidx.compose.runtime.** { *; }
-keep,allowobfuscation class * extends androidx.compose.runtime.RememberObserver

# === KOTLINX SERIALIZATION ===
-keep,includedescriptorclasses class pl.filebit.gymtracker.**$$serializer { *; }
-keepclassmembers class pl.filebit.gymtracker.** {
    *** Companion;
}
-keepclasseswithmembers class pl.filebit.gymtracker.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Generic serializer kotlinx-serialization
-keep,includedescriptorclasses class kotlinx.serialization.** { *; }

# === LOTTIE ===
# JSON-based animations parsowane przez Lottie's reflection-heavy parser
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# === MARKWON ===
# View-based markdown renderer — używa reflection na pluginach
-keep class io.noties.markwon.** { *; }
-dontwarn io.noties.markwon.**

# === COIL ===
-keep class coil.** { *; }
-dontwarn coil.**

# === HEALTH CONNECT ===
-keep class androidx.health.connect.** { *; }
-dontwarn androidx.health.connect.**

# === ML KIT / CAMERAX (barcode scanner) ===
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class androidx.camera.** { *; }

# === AI TOOLS (JSON parsing input/output) ===
-keep class pl.filebit.gymtracker.ai.** { *; }

# === WORKMANAGER ===
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker

# === OKHTTP ===
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# === KOTLIN ===
-keep class kotlin.Metadata { *; }
-keep class kotlin.Result { *; }
