# Add project-specific ProGuard rules here.

# Room: keep entity classes and their fields (accessed via reflection)
-keep class com.lohe.app.data.db.entity.** { *; }
-keepclassmembers class com.lohe.app.data.db.entity.** { *; }

# Keep DAO interfaces (Room generates implementations)
-keep interface com.lohe.app.data.db.dao.** { *; }

# Compose: keep composables and ViewModels referenced via reflection
-keep class androidx.lifecycle.ViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel { *; }

# Media3 consumer rules are applied automatically; keep ExoPlayer entry points
-keep class androidx.media3.exoplayer.** { *; }