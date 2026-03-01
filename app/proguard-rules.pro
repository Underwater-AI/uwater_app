# =============================================================================
# UnderwaterAI — ProGuard / R8 rules
# Designed to maximise obfuscation while keeping the app functional.
# =============================================================================

# ── PyTorch Mobile JNI (must NOT be renamed) ──────────────────────────────
-keep class org.pytorch.** { *; }
-keep class com.facebook.jni.** { *; }
-keep class com.facebook.soloader.** { *; }
-dontwarn org.pytorch.**
-dontwarn com.facebook.**

# ── Kotlin reflection metadata (required by coroutines + serialisation) ───
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Lazy { <methods>; }

# ── Jetpack Compose (compiler-generated classes use reflection) ───────────
-keep class androidx.compose.** { *; }
-keep @androidx.compose.runtime.Composable class * { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ── Lifecycle / ViewModel ─────────────────────────────────────────────────
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ── DataStore (protobuf-lite internals) ───────────────────────────────────
-keep class androidx.datastore.** { *; }
-dontwarn com.google.protobuf.**

# ── Coil (image loading) ──────────────────────────────────────────────────
-dontwarn coil.**
-keep class coil.** { *; }

# ── EXIF interface ────────────────────────────────────────────────────────
-keep class androidx.exifinterface.** { *; }

# ── R8 full-mode aggressiveness ───────────────────────────────────────────
# Repackage all surviving classes into a single flat package to prevent
# reverse-engineering via package structure analysis.
-repackageclasses "x"
-allowaccessmodification
-overloadaggressively
-adaptresourcefilenames
-adaptresourcefilecontents **.properties, META-INF/MANIFEST.MF

# ── Strip debug information ───────────────────────────────────────────────
-renamesourcefileattribute SourceFile
-keepattributes SourceFile, LineNumberTable

# ── Android entry points (Activity, Application, Provider, etc.) ─────────
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ── Parcelables ───────────────────────────────────────────────────────────
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ── Serializable ─────────────────────────────────────────────────────────
-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ── Suppress noisy R8 warnings ───────────────────────────────────────────
-dontwarn java.lang.invoke.**
-dontwarn sun.misc.**
-dontwarn android.annotation.**
-ignorewarnings
