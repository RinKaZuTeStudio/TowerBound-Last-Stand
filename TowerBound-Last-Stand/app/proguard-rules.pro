# TOWERBOUND: LAST STAND — proguard rules
-keepattributes SourceFile,LineNumberTable

# Keep OpenGL JNI-callbacked classes
-keep class com.towerbound.laststand.render.** { *; }

# Keep save data model (serialized via reflection-free JSON, but keep names stable)
-keep class com.towerbound.laststand.save.** { *; }
-keep class com.towerbound.laststand.meta.** { *; }

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
