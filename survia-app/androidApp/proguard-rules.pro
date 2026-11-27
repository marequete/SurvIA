# ─── SurvIA R8 / ProGuard Rules (Phase 1.1) ──────────────────────────────────

# 1. Préserver les bindings JNI MediaPipe Tasks GenAI
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# 2. Préserver les modèles et outils partagés KMP (sérialisation / JSON)
-keep class com.survia.tools.** { *; }
-keepclassmembers class com.survia.tools.** { *; }

# 2b. Préserver Ed25519 fallback (API < 33)
-keep class net.i2p.crypto.eddsa.** { *; }
-dontwarn net.i2p.crypto.eddsa.**

# 3. Kotlinx Serialization & Coroutines
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 4. Suppression des logs de debug en Release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# 5. Durcissement : masquer les noms de fichiers sources et lignes
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
