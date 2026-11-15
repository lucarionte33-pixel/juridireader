# Proguard / R8 rules for Juridireader

# Keep stack trace line numbers and source file names for Play Console deobfuscation
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room entities, DAOs, and Database
-keep class com.juridireader.app.data.local.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}

# Keep WorkManager worker instantiated via reflection
-keepclassmembers class com.juridireader.app.worker.LawSyncWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Keep Backup codec & models
-keep class com.juridireader.app.util.UserBackupPayload { *; }
-keep class com.juridireader.app.util.ImportSummary { *; }
-keep enum com.juridireader.app.util.ImportMode { *; }
