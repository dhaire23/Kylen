# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.takehaire.alpha.**$$serializer { *; }
-keepclassmembers class com.takehaire.alpha.** { *** Companion; }
-keepclasseswithmembers class com.takehaire.alpha.** { kotlinx.serialization.KSerializer serializer(...); }
# Bundled JSON assets are read as Java resources.
-keep class com.takehaire.alpha.imaging.calib.Assets { *; }
