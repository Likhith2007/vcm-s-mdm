# Keep kotlinx.serialization generated serializers for protocol classes.
-keepclassmembers class com.mdmesh.proto.** {
    *** Companion;
}
-keepclasseswithmembers class com.mdmesh.proto.** {
    kotlinx.serialization.KSerializer serializer(...);
}
