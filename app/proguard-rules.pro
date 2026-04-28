# Add project specific ProGuard rules here.
# For most apps, the default rules are sufficient.

# Keep generic signatures for serialization
-keepattributes Signature
-keepattributes *Annotation*

# Kotlinx serialization
-keep,includedescriptorclasses class pl.filebit.gymtracker.**$$serializer { *; }
-keepclassmembers class pl.filebit.gymtracker.** {
    *** Companion;
}
-keepclasseswithmembers class pl.filebit.gymtracker.** {
    kotlinx.serialization.KSerializer serializer(...);
}
