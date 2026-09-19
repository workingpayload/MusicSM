package com.example.musicsm.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.musicsm.data.local.MusicDatabase
import com.example.musicsm.data.local.dao.ArtistDao
import com.example.musicsm.data.local.dao.DownloadDao
import com.example.musicsm.data.local.dao.HistoryDao
import com.example.musicsm.data.local.dao.LikeDao
import com.example.musicsm.data.local.dao.PlaylistDao
import com.example.musicsm.data.local.dao.SongDao
import com.example.musicsm.data.local.dao.StatsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playlists ADD COLUMN artworkUrl TEXT")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS liked_artists " +
                    "(artistId TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, artworkUrl TEXT, likedAt INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS play_history " +
                    "(songId TEXT NOT NULL PRIMARY KEY, playedAt INTEGER NOT NULL)",
            )
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS downloads " +
                    "(songId TEXT NOT NULL PRIMARY KEY, filePath TEXT NOT NULL, mimeType TEXT, downloadedAt INTEGER NOT NULL)",
            )
        }
    }

    /**
     * Adds foreign keys (with `ON DELETE CASCADE`) to every table that references a song or a
     * playlist. SQLite cannot add a constraint in place, so each table is rebuilt: orphaned rows
     * are dropped first (they would fail the new constraint), then the data is copied across.
     *
     * `defer_foreign_keys` holds enforcement until the end of Room's migration transaction, which
     * is what lets the tables be swapped out from under each other.
     */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA defer_foreign_keys = TRUE")

            // Before 4->5 nothing stopped a membership/like/history/download row from outliving
            // its song, so clear those out before the constraint starts being enforced.
            db.execSQL("DELETE FROM playlist_songs WHERE songId NOT IN (SELECT songId FROM songs)")
            db.execSQL("DELETE FROM playlist_songs WHERE playlistId NOT IN (SELECT playlistId FROM playlists)")
            db.execSQL("DELETE FROM liked_songs WHERE songId NOT IN (SELECT songId FROM songs)")
            db.execSQL("DELETE FROM play_history WHERE songId NOT IN (SELECT songId FROM songs)")
            db.execSQL("DELETE FROM downloads WHERE songId NOT IN (SELECT songId FROM songs)")

            db.execSQL(
                "CREATE TABLE playlist_songs_new (" +
                    "playlistId INTEGER NOT NULL, songId TEXT NOT NULL, position INTEGER NOT NULL, " +
                    "PRIMARY KEY(playlistId, songId), " +
                    "FOREIGN KEY(playlistId) REFERENCES playlists(playlistId) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(songId) REFERENCES songs(songId) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "INSERT INTO playlist_songs_new (playlistId, songId, position) " +
                    "SELECT playlistId, songId, position FROM playlist_songs",
            )
            db.execSQL("DROP TABLE playlist_songs")
            db.execSQL("ALTER TABLE playlist_songs_new RENAME TO playlist_songs")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_songs_playlistId ON playlist_songs(playlistId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_songs_songId ON playlist_songs(songId)")

            db.execSQL(
                "CREATE TABLE liked_songs_new (" +
                    "songId TEXT NOT NULL, likedAt INTEGER NOT NULL, PRIMARY KEY(songId), " +
                    "FOREIGN KEY(songId) REFERENCES songs(songId) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL("INSERT INTO liked_songs_new (songId, likedAt) SELECT songId, likedAt FROM liked_songs")
            db.execSQL("DROP TABLE liked_songs")
            db.execSQL("ALTER TABLE liked_songs_new RENAME TO liked_songs")

            db.execSQL(
                "CREATE TABLE play_history_new (" +
                    "songId TEXT NOT NULL, playedAt INTEGER NOT NULL, PRIMARY KEY(songId), " +
                    "FOREIGN KEY(songId) REFERENCES songs(songId) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL("INSERT INTO play_history_new (songId, playedAt) SELECT songId, playedAt FROM play_history")
            db.execSQL("DROP TABLE play_history")
            db.execSQL("ALTER TABLE play_history_new RENAME TO play_history")

            db.execSQL(
                "CREATE TABLE downloads_new (" +
                    "songId TEXT NOT NULL, filePath TEXT NOT NULL, mimeType TEXT, " +
                    "downloadedAt INTEGER NOT NULL, PRIMARY KEY(songId), " +
                    "FOREIGN KEY(songId) REFERENCES songs(songId) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "INSERT INTO downloads_new (songId, filePath, mimeType, downloadedAt) " +
                    "SELECT songId, filePath, mimeType, downloadedAt FROM downloads",
            )
            db.execSQL("DROP TABLE downloads")
            db.execSQL("ALTER TABLE downloads_new RENAME TO downloads")
        }
    }

    /**
     * Adds the append-only `play_events` log that the listening-stats screen aggregates.
     * `play_history` only ever holds one row per song (its primary key is `songId`), so play
     * counts were impossible before this. Existing history is seeded in as one event per song
     * so the first stats screen isn't completely empty.
     */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `play_events` (" +
                    "`eventId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`songId` TEXT NOT NULL, `playedAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`songId`) REFERENCES `songs`(`songId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_events_songId` ON `play_events` (`songId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_events_playedAt` ON `play_events` (`playedAt`)")
            db.execSQL(
                "INSERT INTO play_events (songId, playedAt) " +
                    "SELECT songId, playedAt FROM play_history " +
                    "WHERE songId IN (SELECT songId FROM songs)",
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MusicDatabase =
        Room.databaseBuilder(context, MusicDatabase::class.java, "musicsm.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()

    @Provides
    fun provideSongDao(db: MusicDatabase): SongDao = db.songDao()

    @Provides
    fun provideLikeDao(db: MusicDatabase): LikeDao = db.likeDao()

    @Provides
    fun providePlaylistDao(db: MusicDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideArtistDao(db: MusicDatabase): ArtistDao = db.artistDao()

    @Provides
    fun provideHistoryDao(db: MusicDatabase): HistoryDao = db.historyDao()

    @Provides
    fun provideStatsDao(db: MusicDatabase): StatsDao = db.statsDao()

    @Provides
    fun provideDownloadDao(db: MusicDatabase): DownloadDao = db.downloadDao()
}
