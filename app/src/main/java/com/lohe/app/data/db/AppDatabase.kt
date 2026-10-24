package com.lohe.app.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lohe.app.data.db.dao.EpisodeDao
import com.lohe.app.data.db.dao.ListeningHistoryDao
import com.lohe.app.data.db.dao.PodcastDao
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.ListeningHistoryEntity
import com.lohe.app.data.db.entity.PodcastEntity

/**
 * @see version 2 added `episodes.queuePosition` / `episodes.lastPlayedAt`;
 *   version 3 added `episodes.localPath` / `episodes.downloadState` for offline
 *   playback; version 4 added `podcasts.autoDownload` for per-show settings.
 *   All are covered by real migrations — destructive fallback would throw away
 *   a user's entire library on upgrade, which is data loss, not a convenience.
 */
@Database(
    entities = [
        PodcastEntity::class,
        EpisodeEntity::class,
        ListeningHistoryEntity::class,
    ],
    version = 12,
    exportSchema = false,
)
@TypeConverters()
abstract class AppDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun listeningHistoryDao(): ListeningHistoryDao

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
         * Actual listening time, tracked separately from the resume position.
         *
         * Seeded from `playedUpToSeconds` so a user who has been listening
         * keeps their history: for an episode already played, the position was
         * cleared to zero when it was marked played, so there is nothing left
         * to carry over and the total starts from what the app knows today.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE episodes ADD COLUMN listenedSeconds INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "UPDATE episodes SET listenedSeconds = playedUpToSeconds " +
                        "WHERE playingStatus = 1 AND playedUpToSeconds > 0",
                )
            }
        }

        /**
         * Listening history for episodes whose podcast has been removed.
         *
         * Unsubscribing deletes the podcast row and the episode foreign key
         * cascades from there, so without this table the accumulated listening
         * time would be destroyed along with the show. Seeded from the
         * episodes that still carry a listened time so totals do not drop on
         * upgrade.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS listening_history (" +
                        "episodeUuid TEXT NOT NULL, " +
                        "listenedSeconds INTEGER NOT NULL, " +
                        "durationSeconds INTEGER, " +
                        "title TEXT, " +
                        "PRIMARY KEY(episodeUuid))",
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO listening_history " +
                        "(episodeUuid, listenedSeconds, durationSeconds, title) " +
                        "SELECT uuid, listenedSeconds, durationSeconds, title " +
                        "FROM episodes WHERE listenedSeconds > 0",
                )
            }
        }

        /**
         * Chapter markers: JSON text column on episodes for chapter navigation.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE episodes ADD COLUMN chapters TEXT DEFAULT NULL",
                )
            }
        }

        /**
         * Episodes filed away by the listener.
         *
         * Kept rather than deleted, so an episode put aside can be brought
         * back, but left out of the lists and of the listening figures.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE episodes ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcasts ADD COLUMN isSubscribed INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE episodes ADD COLUMN playedAt INTEGER DEFAULT NULL")
                db.execSQL("UPDATE episodes SET playedAt = CAST(strftime('%s', 'now') AS INTEGER) * 1000 WHERE playingStatus = 2 AND isArchived = 0")
            }
        }

        /** Per-podcast auto-queue behavior for episodes discovered on refresh. */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcasts ADD COLUMN autoAddNewEpisodesToUpNext INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE podcasts ADD COLUMN autoAddNewEpisodesAtTop INTEGER NOT NULL DEFAULT 0")
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
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                    )
                    .build()
                    .also { instance = it }
            }
    }
}
