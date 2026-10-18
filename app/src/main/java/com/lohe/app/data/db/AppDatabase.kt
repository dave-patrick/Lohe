package com.lohe.app.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lohe.app.data.db.dao.EpisodeDao
import com.lohe.app.data.db.dao.PodcastDao
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.PodcastEntity

/**
 * @see version 2 added `episodes.queuePosition` / `episodes.lastPlayedAt`;
 *   version 3 added `episodes.localPath` / `episodes.downloadState` for offline
 *   playback; version 4 added `podcasts.autoDownload` for per-show settings.
 *   All are covered by real migrations — destructive fallback would throw away
 *   a user's entire library on upgrade, which is data loss, not a convenience.
 */
@Database(
    entities = [PodcastEntity::class, EpisodeEntity::class],
    version = 5,
    exportSchema = false,
)
@TypeConverters()
abstract class AppDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * Adds the queue and offline-download columns.
         *
         * Nullable with a NULL default: existing rows must read as "not queued"
         * and "not downloaded", not as queue position 0 — which is a real
         * (head-of-queue) position and would make every episode look queued.
         */
        private val ADD_QUEUE_AND_DOWNLOAD_COLUMNS = arrayOf(
            "ALTER TABLE episodes ADD COLUMN queuePosition INTEGER DEFAULT NULL",
            "ALTER TABLE episodes ADD COLUMN lastPlayedAt INTEGER DEFAULT NULL",
            "ALTER TABLE episodes ADD COLUMN localPath TEXT DEFAULT NULL",
            "ALTER TABLE episodes ADD COLUMN downloadState INTEGER NOT NULL DEFAULT 0",
        )

        /** "Continue listening" and the queue both order on these. */
        private val ADD_PERFORMANCE_INDEXES = arrayOf(
            "CREATE INDEX IF NOT EXISTS index_episodes_inProgress " +
                "ON episodes (playingStatus, lastPlayedAt)",
            "CREATE INDEX IF NOT EXISTS index_episodes_queuePosition " +
                "ON episodes (queuePosition)",
        )

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ADD_QUEUE_AND_DOWNLOAD_COLUMNS.forEach(db::execSQL)
                ADD_PERFORMANCE_INDEXES.forEach(db::execSQL)
            }
        }

        /**
         * Per-show auto-download. Defaults to off everywhere, so upgrading
         * never starts moving data the user did not ask to move.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE podcasts ADD COLUMN autoDownload INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE episodes ADD COLUMN description TEXT DEFAULT NULL",
                )
            }
        }

        /**
         * Installs that predate the queue feature still exist in the wild, so
         * there has to be a path from the original schema too — otherwise Room
         * throws on upgrade and the app dies on first launch.
         */
        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ADD_QUEUE_AND_DOWNLOAD_COLUMNS.forEach(db::execSQL)
                ADD_PERFORMANCE_INDEXES.forEach(db::execSQL)
            }
        }

        /** Straight shot for anyone upgrading across both changes at once. */
        val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ADD_QUEUE_AND_DOWNLOAD_COLUMNS.forEach(db::execSQL)
                ADD_PERFORMANCE_INDEXES.forEach(db::execSQL)
                db.execSQL(
                    "ALTER TABLE podcasts ADD COLUMN autoDownload INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        fun get(context: android.content.Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lohe.db",
                )
                    .addMigrations(
                        MIGRATION_1_3,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_1_4,
                        MIGRATION_4_5,
                    )
                    .build()
                    .also { instance = it }
            }
    }
}