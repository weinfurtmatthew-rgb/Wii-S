# kotlinx.serialization ships its own consumer rules; registry models are @Serializable.
-keepattributes *Annotation*, InnerClasses

# Keep names and line numbers so crash reports (CrashLog) read as written. The launcher is
# sideloaded; readable reports are worth the slightly larger APK.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
