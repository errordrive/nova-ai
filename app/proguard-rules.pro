# ProGuard rules for Nova AI Chat & Agent Workspace

# --- kotlinx.serialization ---
-keepattributes *Annotation*
-keepattributes Signature
-keep class kotlinx.serialization.** { *; }
-keepclasseswithmembernames class * {
    @kotlinx.serialization.Serializable *;
}

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers,allowshrinking class * {
    @androidx.room.Embedded *;
}
-dontwarn androidx.room.**

# --- Hilt / Dagger ---
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# --- OkHttp / okio ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Nova domain models: keep names for readable serialization errors and logging ---
-keepnames class com.nova.ai.provider.** { *; }
-keepnames class com.nova.ai.agent.** { *; }

# --- Compose ---
-keepattributes SourceFile,LineNumberTable
