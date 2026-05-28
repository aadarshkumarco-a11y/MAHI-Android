# ============================================
# MAHI Android AI Assistant - ProGuard Rules
# ============================================

# --------------------------------------------
# General Android Rules
# --------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# --------------------------------------------
# Retrofit & OkHttp
# --------------------------------------------
# Retrofit does reflection on generic parameters. Without this, Retrofit service methods
# may return incorrect types when ProGuard strips generic type info.
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# Retrofit
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Don't remove Retrofit interface implementations
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keepclassmembers class okhttp3.internal.http.* { *; }

# OkHttp Platform
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --------------------------------------------
# Gson
# --------------------------------------------
# Gson uses generic type information stored in a class file when working with fields.
# Proguard removes such information by default, so configure it to keep all of it.
-keepattributes Signature

# For using GSON @Expose annotation
-keepattributes *Annotation*

# Gson specific classes
-dontwarn sun.misc.**

# Prevent R8 from stripping interface information from TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Application data classes that are serialized/deserialized over Gson
-keep class com.mahi.assistant.data.model.** { *; }

# Keep all model class fields for Gson serialization
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Prevent R8 from stripping interface information from TypeAdapter
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# --------------------------------------------
# Hilt / Dagger
# --------------------------------------------
-dontwarn dagger.**
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Hilt generated components
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }

# Keep classes annotated with Hilt annotations
-keepclasseswithmembers class * {
    @javax.inject.Inject <init>(...);
}
-keepclasseswithmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# Hilt entry points
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }
-keep class **_HiltComponents* { *; }
-keep class **_HiltModules* { *; }
-keep class **_GeneratedInjector { *; }
-keep class **_HiltS{ *; }

# --------------------------------------------
# Room Database
# --------------------------------------------
# Room entity classes
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }

# Room DAO interfaces
-keep class * implements androidx.room.Dao { *; }
-keepclasseswithmembers class * {
    @androidx.room.Query <methods>;
}
-keepclasseswithmembers class * {
    @androidx.room.Insert <methods>;
}
-keepclasseswithmembers class * {
    @androidx.room.Update <methods>;
}
-keepclasseswithmembers class * {
    @androidx.room.Delete <methods>;
}
-keepclasseswithmembers class * {
    @androidx.room.RawQuery <methods>;
}

# Keep Room TypeConverters
-keep class * implements androidx.room.TypeConverter { *; }
-keepclasseswithmembers class * {
    @androidx.room.TypeConverters <methods>;
}

# Keep embedded fields
-keepclassmembers,allowobfuscation class * {
    @androidx.room.Embedded <fields>;
}

# Keep Relation fields
-keepclassmembers,allowobfuscation class * {
    @androidx.room.Relation <fields>;
}

# Room generated classes
-keep class **_Impl { *; }
-keep class **_HiltComponents** { *; }

# --------------------------------------------
# Porcupine (Wake Word Detection)
# --------------------------------------------
-keep class ai.picovoice.porcupine.** { *; }
-keep class ai.picovoice.porcupine.Porcupine { *; }
-keep class ai.picovoice.porcupine.PorcupineException { *; }
-keep class ai.picovoice.porcupine.PorcupineManager { *; }

# Keep native methods for Porcupine
-keepclasseswithmembernames class * {
    native <methods>;
}

# --------------------------------------------
# AndroidX / Jetpack Compose
# --------------------------------------------
-dontwarn androidx.**
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Lifecycle
-keep class androidx.lifecycle.** { *; }

# ViewModel
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { <init>(...); }

# --------------------------------------------
# Kotlin Coroutines
# --------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# --------------------------------------------
# Kotlin Serialization
# --------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.mahi.assistant.**$$serializer { *; }
-keepclassmembers class com.mahi.assistant.** {
    *** Companion;
}
-keepclasseswithmembers class com.mahi.assistant.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --------------------------------------------
# Speech Recognition
# --------------------------------------------
-keep class android.speech.** { *; }
-keep class java.util.Locale { *; }

# --------------------------------------------
# Project-specific keep rules
# --------------------------------------------
# Keep all data model classes (used with Gson serialization)
-keep class com.mahi.assistant.data.model.** { *; }
-keep class com.mahi.assistant.data.local.** { *; }
-keep class com.mahi.assistant.data.remote.** { *; }

# Keep service classes (referenced by AndroidManifest)
-keep class com.mahi.assistant.service.** { *; }
-keep class com.mahi.assistant.receiver.** { *; }

# Keep Hilt modules
-keep class com.mahi.assistant.di.** { *; }

# Keep enums (used in Intent classification)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
