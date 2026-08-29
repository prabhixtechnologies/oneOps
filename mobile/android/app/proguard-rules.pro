# R8 rules for the release build (isMinifyEnabled = true).
#
# Everything here exists because the code is reached by reflection or by generated code that R8
# cannot see statically. A missing rule does not fail the build — it produces an APK that
# installs and then crashes on first use, so treat changes here as release-blocking.

# ---------------------------------------------------------------------------
# Kotlinx Serialization
# ---------------------------------------------------------------------------
# Serializers are generated as `Companion.serializer()` / `$serializer` members that nothing calls
# directly; R8 sees them as dead code and strips them, and every response then fails to parse.
# These are the rules published in the kotlinx.serialization README.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Keep `Companion` object fields of serializable classes, so the serializer is not looked up
# through getDeclaredClasses().
-if @kotlinx.serialization.Serializable class **
-keepclassmembers public class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# The API DTOs, belt and braces: these are the classes whose parsing failures would be hardest to
# diagnose from a stripped stack trace in the field.
-keep class com.prabhix.operator.data.api.** { *; }

# ---------------------------------------------------------------------------
# Retrofit
# ---------------------------------------------------------------------------
# Retrofit reads generic types and annotations off the service interfaces at runtime. Signature
# needs InnerClasses, which needs EnclosingMethod.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleParameterAnnotations

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# R8 in full mode strips generic signatures from classes it does not keep, which breaks
# suspend-function return types on Retrofit services.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**

# ---------------------------------------------------------------------------
# OkHttp — optional TLS providers referenced but not bundled
# ---------------------------------------------------------------------------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------------------------------------------------------------------------
# Tink, via androidx.security-crypto (EncryptedSharedPreferences in TokenStore)
# ---------------------------------------------------------------------------
# Tink carries Error Prone's compile-time annotations, which are not on the runtime classpath. They
# were reaching R8 transitively through Firebase; now that Firebase is a OneOps-only dependency, the
# admin build has to declare this itself. Annotations are erased at runtime, so there is nothing to
# keep here - only the warning to silence.
-dontwarn com.google.errorprone.annotations.**
