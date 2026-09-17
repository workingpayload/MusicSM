# ---------------------------------------------------------------------------
# MusicSM R8 configuration
#
# Most libraries here (OkHttp, Okio, Room, Hilt, Media3, Coil, Compose,
# coroutines) ship their own consumer rules inside their AARs, so this file only
# covers what R8 cannot infer on its own: reflection-driven code.
# ---------------------------------------------------------------------------

# Keep the line numbers/file names so release crash reports stay readable, but
# still let R8 rename everything else.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Annotations and generic signatures are needed by Room, Hilt and Gson-style
# reflection, and by Kotlin's own reified/type-token machinery.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# ---------------------------------------------------------------------------
# NewPipeExtractor
#
# The extractor resolves service/linkhandler/extractor classes reflectively and
# evaluates YouTube's player JS through Rhino. Shrinking or renaming any of it
# breaks stream resolution at runtime, which is invisible until playback fails,
# so the whole extractor and its parsing stack is kept verbatim.
# ---------------------------------------------------------------------------
-keep class org.schabi.newpipe.extractor.** { *; }
-keep interface org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# Rhino — NewPipe deobfuscates the player script with it; Rhino itself is a
# reflective JS engine and must not be touched.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-dontwarn org.mozilla.javascript.**

# jsoup, nanojson and Rhino's optional deps referenced by NewPipe.
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
-keep class com.grack.nanojson.** { *; }
-dontwarn com.grack.nanojson.**

# NewPipe pulls in java.nio.file / javax.annotation pieces that are absent on
# Android; desugaring handles the ones we actually execute.
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontwarn javax.lang.model.**
-dontwarn org.ietf.jgss.**

# ---------------------------------------------------------------------------
# Room
#
# Generated DAOs access entity fields directly, so entities must keep their
# field names even though the classes themselves could be renamed.
# ---------------------------------------------------------------------------
-keep class com.example.musicsm.data.local.entity.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static <methods>;
}

# ---------------------------------------------------------------------------
# App model + Wear data-layer contract
#
# Domain models are plain data classes, but they cross the Media3 session and
# Wear Data Layer boundaries by field name, so keep their members.
# ---------------------------------------------------------------------------
-keep class com.example.musicsm.domain.model.** { *; }
-keep class com.example.musicsm.wear.WearContract { *; }

# ---------------------------------------------------------------------------
# Framework entry points declared only in the manifest
# ---------------------------------------------------------------------------
-keep class com.example.musicsm.playback.PlaybackService { *; }
-keep class com.example.musicsm.download.** { *; }
-keep class com.example.musicsm.tile.PlaybackTileService { *; }
-keep class com.example.musicsm.widget.NowPlayingWidgetProvider { *; }
-keep class com.example.musicsm.wear.WearBridgeService { *; }

# ---------------------------------------------------------------------------
# Kotlin / coroutines
# ---------------------------------------------------------------------------
-keepclassmembers class kotlin.Metadata { public <methods>; }
-dontwarn kotlinx.coroutines.**

# Enum valueOf/values are used by SongSort.fromName and by Media3 state mapping.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------------------------------------------------------------------------
# Play Services (Wear Data Layer) — safe defaults from the SDK docs.
# ---------------------------------------------------------------------------
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
