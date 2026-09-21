# Firebase / Firestore
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class * { @com.google.firebase.firestore.PropertyName <fields>; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Kotlinx Serialization
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.familyfinance.app.**$$serializer { *; }
-keepclassmembers class com.familyfinance.app.** { *** Companion; }
-keepclasseswithmembers class com.familyfinance.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Exceptions

# Coroutines
-dontwarn kotlinx.coroutines.**
