# Manifest entry points (provisioning / kiosk launcher) — kept explicitly.
-keep class com.mdmesh.agent.admin.AdminReceiver { *; }
-keep class com.mdmesh.agent.KioskLauncherActivity { *; }

# --- kotlinx.serialization (annotation-based, package-agnostic) ---
# Covers @Serializable DTOs in :proto AND the private Payload classes in :core handlers, which a
# proto-only rule would miss → release-only serialization crashes.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, AnnotationDefault
-dontnote kotlinx.serialization.**

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-keepclasseswithmembers class **$$serializer { *; }
-keepclassmembers class **$$serializer { *; }
-keepclassmembers class * {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Retrofit / OkHttp ---
-keep,allowobfuscation interface com.mdmesh.core.net.MdmApi
# Package-agnostic keep for every Retrofit service interface (the official Retrofit rule),
# so a package rename can never silently orphan the explicit rule above again.
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
