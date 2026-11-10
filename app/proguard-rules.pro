# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve source file and line numbers so local crash logs stay readable
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room @Database, @Dao, and @Entity classes
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class com.example.data.AppDatabase** { *; }
-keep class com.example.data.dao.** { *; }
-keep class com.example.data.model.** { *; }

# WorkManager CoroutineWorker / ListenableWorker constructors
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.example.worker.** { *; }

# DataStore Preferences
-keep class androidx.datastore.** { *; }
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}
-keep class com.example.data.datastore.** { *; }

# Models and backup classes touched via JSONObject or reflection
-keep class com.example.importbackup.** { *; }
-keep class com.example.domain.model.** { *; }
