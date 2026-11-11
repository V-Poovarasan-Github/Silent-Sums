package com.example.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.room.Room
import com.example.data.datastore.AppSettingsRepository
import com.example.data.repository.MediaRepository
import com.example.data.seed.SampleDataSeeder
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StorageBundle(
    val database: AppDatabase,
    val mediaRepository: MediaRepository,
    val isDemo: Boolean
)

object DatabaseProvider {
    private const val TAG = "DatabaseProvider"

    private val currentBundle = AtomicReference<StorageBundle?>(null)
    private val abandonedDatabases = ConcurrentLinkedQueue<AppDatabase>()

    @Volatile
    var isFlippingMode: Boolean = false

    private val lock = Any()

    fun isDemoMode(context: Context): Boolean {
        val marker = File(context.filesDir, StorageConstants.DEMO_MARKER_FILE)
        return marker.exists()
    }

    fun getBundle(context: Context): StorageBundle {
        val existing = currentBundle.get()
        if (existing != null) return existing
        return synchronized(lock) {
            val doubleCheck = currentBundle.get()
            if (doubleCheck != null) {
                doubleCheck
            } else {
                val built = resolveAndBuildBundle(context)
                currentBundle.set(built)
                built
            }
        }
    }

    fun getDatabase(context: Context): AppDatabase {
        return getBundle(context).database
    }

    fun getMediaRepository(context: Context): MediaRepository {
        return getBundle(context).mediaRepository
    }

    private fun resolveAndBuildBundle(context: Context): StorageBundle {
        val isDemo = isDemoMode(context)
        val demoDbFile = context.getDatabasePath(StorageConstants.DEMO_DATABASE_NAME)
        return if (isDemo && demoDbFile.exists()) {
            try {
                val demoDb = buildDatabase(context, StorageConstants.DEMO_DATABASE_NAME)
                // Validation query on cold start: must have sample transactions
                val count = demoDb.openHelper.readableDatabase.query("SELECT COUNT(*) FROM transactions").use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
                if (count == 0) {
                    throw IllegalStateException("Empty/unseeded demo database detected on cold start")
                }
                val mediaRepo = MediaRepository(context, demoDb)
                StorageBundle(demoDb, mediaRepo, isDemo = true)
            } catch (t: Throwable) {
                if (com.example.BuildConfig.DEBUG) {
                    Log.e(TAG, "Corrupt or unseeded demo database detected; falling back to real mode", t)
                }
                purgeDemoLeftovers(context)
                val realDb = buildDatabase(context, StorageConstants.REAL_DATABASE_NAME)
                val mediaRepo = MediaRepository(context, realDb)
                StorageBundle(realDb, mediaRepo, isDemo = false)
            }
        } else {
            // Clean up any stray demo files if marker is absent or demo DB missing
            purgeDemoLeftovers(context)
            val realDb = buildDatabase(context, StorageConstants.REAL_DATABASE_NAME)
            val mediaRepo = MediaRepository(context, realDb)
            StorageBundle(realDb, mediaRepo, isDemo = false)
        }
    }

    fun buildDatabase(context: Context, dbName: String): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            dbName
        ).addMigrations(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
            AppDatabase.MIGRATION_8_9,
            AppDatabase.MIGRATION_9_10
        ).build()
    }

    fun purgeDemoLeftovers(context: Context) {
        try {
            val marker = File(context.filesDir, StorageConstants.DEMO_MARKER_FILE)
            if (marker.exists()) marker.delete()
            context.deleteDatabase(StorageConstants.DEMO_DATABASE_NAME)
            val demoMedia = File(context.filesDir, StorageConstants.DEMO_MEDIA_DIR)
            if (demoMedia.exists()) demoMedia.deleteRecursively()
            val demoStaging = File(context.cacheDir, StorageConstants.DEMO_STAGING_DIR)
            if (demoStaging.exists()) demoStaging.deleteRecursively()
        } catch (e: Exception) {
            if (com.example.BuildConfig.DEBUG) {
                Log.w(TAG, "Failed to purge demo leftovers: ${e.message}")
            }
        }
    }

    /**
     * Shared flip function used by BOTH entry and exit.
     * Contract: All file and DataStore operations run on Dispatchers.IO.
     * The outgoing Room database is NEVER closed during the flip.
     * The bundle reference is updated via an atomic reference assignment.
     * The final UI recreation runs strictly on Dispatchers.Main.immediate.
     */
    suspend fun flipTo(context: Context, settingsRepository: AppSettingsRepository, targetDemoMode: Boolean) {
        val newBundle = withContext(Dispatchers.IO) {
            synchronized(lock) {
                isFlippingMode = true
            }
            try {
                if (targetDemoMode) {
                    // Step 1: Open / build isolated demo DB and seed it while marker is still ABSENT
                    val demoDb = buildDatabase(context, StorageConstants.DEMO_DATABASE_NAME)
                    SampleDataSeeder.seedSampleData(demoDb)

                    // Step 2: Ensure demo media and demo staging dirs exist
                    File(context.filesDir, StorageConstants.DEMO_MEDIA_DIR).mkdirs()
                    File(context.cacheDir, StorageConstants.DEMO_STAGING_DIR).mkdirs()

                    // Step 3: Write marker file atomically (temp then rename)
                    val marker = File(context.filesDir, StorageConstants.DEMO_MARKER_FILE)
                    val temp = File(context.filesDir, "${StorageConstants.DEMO_MARKER_FILE}.tmp")
                    temp.writeBytes(ByteArray(0))
                    temp.renameTo(marker)

                    // Step 4: Mirror to DataStore
                    settingsRepository.setDemoMode(true)

                    // Step 5: Build new demo bundle
                    val mediaRepo = MediaRepository(context, demoDb)
                    StorageBundle(demoDb, mediaRepo, isDemo = true)
                } else {
                    // Step 1: Delete marker FIRST
                    val marker = File(context.filesDir, StorageConstants.DEMO_MARKER_FILE)
                    if (marker.exists()) marker.delete()

                    // Step 2: Mirror to DataStore
                    settingsRepository.setDemoMode(false)

                    // Step 3: Build new real bundle
                    val realDb = buildDatabase(context, StorageConstants.REAL_DATABASE_NAME)
                    val mediaRepo = MediaRepository(context, realDb)
                    val bundle = StorageBundle(realDb, mediaRepo, isDemo = false)

                    // Step 4: Purge demo leftovers
                    purgeDemoLeftovers(context)
                    bundle
                }
            } finally {
                synchronized(lock) {
                    isFlippingMode = false
                }
            }
        }

        // Atomic swap of the bundle reference; old instance is never closed here!
        val old = currentBundle.getAndSet(newBundle)
        if (old != null && old.database !== newBundle.database) {
            abandonedDatabases.add(old.database)
        }

        // Step 6: Recreate UI on Main thread if Activity is present
        findActivity(context)?.let { activity ->
            activity.runOnUiThread {
                activity.recreate()
            }
        }
    }

    suspend fun enterDemoMode(context: Context, settingsRepository: AppSettingsRepository) {
        flipTo(context, settingsRepository, targetDemoMode = true)
    }

    suspend fun exitDemoMode(context: Context, settingsRepository: AppSettingsRepository) {
        flipTo(context, settingsRepository, targetDemoMode = false)
    }

    fun resetInstance() {
        synchronized(lock) {
            val old = currentBundle.getAndSet(null)
            if (old != null) {
                abandonedDatabases.add(old.database)
            }
        }
    }

    fun closeAbandonedDatabases() {
        while (true) {
            val db = abandonedDatabases.poll() ?: break
            try {
                db.close()
            } catch (e: Throwable) {
                if (com.example.BuildConfig.DEBUG) {
                    Log.w(TAG, "Swallowed error closing abandoned database", e)
                }
            }
        }
    }

    fun findActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
