-- Replays the Room v4 -> v5 migration against real SQLite and asserts the outcome.
-- Run with: sqlite3.exe :memory: ".read migration_check.sql"

-- ---------- v4 schema, exactly as migrations 1..4 leave it ----------
CREATE TABLE songs (songId TEXT NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, album TEXT, artworkUrl TEXT, durationMs INTEGER NOT NULL, PRIMARY KEY(songId));
CREATE TABLE playlists (playlistId INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, createdAt INTEGER NOT NULL, artworkUrl TEXT);
CREATE TABLE playlist_songs (playlistId INTEGER NOT NULL, songId TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(playlistId, songId));
CREATE INDEX index_playlist_songs_playlistId ON playlist_songs(playlistId);
CREATE INDEX index_playlist_songs_songId ON playlist_songs(songId);
CREATE TABLE liked_songs (songId TEXT NOT NULL PRIMARY KEY, likedAt INTEGER NOT NULL);
CREATE TABLE liked_artists (artistId TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, artworkUrl TEXT, likedAt INTEGER NOT NULL);
CREATE TABLE play_history (songId TEXT NOT NULL PRIMARY KEY, playedAt INTEGER NOT NULL);
CREATE TABLE downloads (songId TEXT NOT NULL PRIMARY KEY, filePath TEXT NOT NULL, mimeType TEXT, downloadedAt INTEGER NOT NULL);

-- Seed: valid rows plus deliberate orphans the new constraints would reject.
INSERT INTO songs VALUES ('s1','A','Artist',NULL,NULL,1000);
INSERT INTO songs VALUES ('s2','B','Artist',NULL,NULL,2000);
INSERT INTO playlists (playlistId,name,createdAt,artworkUrl) VALUES (1,'P',0,NULL);
INSERT INTO playlist_songs VALUES (1,'s1',0);
INSERT INTO playlist_songs VALUES (1,'s2',1);
INSERT INTO playlist_songs VALUES (1,'ghost',2);
INSERT INTO playlist_songs VALUES (99,'s1',0);
INSERT INTO liked_songs VALUES ('s1',10);
INSERT INTO liked_songs VALUES ('ghost',10);
INSERT INTO play_history VALUES ('s2',20);
INSERT INTO play_history VALUES ('ghost',20);
INSERT INTO downloads VALUES ('s1','/tmp/a.m4a','audio/mp4',30);
INSERT INTO downloads VALUES ('ghost','/tmp/x','audio/mp4',30);

-- ---------- MIGRATION_4_5, transcribed from DatabaseModule ----------
BEGIN;
PRAGMA defer_foreign_keys = TRUE;
DELETE FROM playlist_songs WHERE songId NOT IN (SELECT songId FROM songs);
DELETE FROM playlist_songs WHERE playlistId NOT IN (SELECT playlistId FROM playlists);
DELETE FROM liked_songs WHERE songId NOT IN (SELECT songId FROM songs);
DELETE FROM play_history WHERE songId NOT IN (SELECT songId FROM songs);
DELETE FROM downloads WHERE songId NOT IN (SELECT songId FROM songs);

CREATE TABLE playlist_songs_new (playlistId INTEGER NOT NULL, songId TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(playlistId, songId), FOREIGN KEY(playlistId) REFERENCES playlists(playlistId) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(songId) REFERENCES songs(songId) ON UPDATE NO ACTION ON DELETE CASCADE);
INSERT INTO playlist_songs_new (playlistId, songId, position) SELECT playlistId, songId, position FROM playlist_songs;
DROP TABLE playlist_songs;
ALTER TABLE playlist_songs_new RENAME TO playlist_songs;
CREATE INDEX IF NOT EXISTS index_playlist_songs_playlistId ON playlist_songs(playlistId);
CREATE INDEX IF NOT EXISTS index_playlist_songs_songId ON playlist_songs(songId);

CREATE TABLE liked_songs_new (songId TEXT NOT NULL, likedAt INTEGER NOT NULL, PRIMARY KEY(songId), FOREIGN KEY(songId) REFERENCES songs(songId) ON UPDATE NO ACTION ON DELETE CASCADE);
INSERT INTO liked_songs_new (songId, likedAt) SELECT songId, likedAt FROM liked_songs;
DROP TABLE liked_songs;
ALTER TABLE liked_songs_new RENAME TO liked_songs;

CREATE TABLE play_history_new (songId TEXT NOT NULL, playedAt INTEGER NOT NULL, PRIMARY KEY(songId), FOREIGN KEY(songId) REFERENCES songs(songId) ON UPDATE NO ACTION ON DELETE CASCADE);
INSERT INTO play_history_new (songId, playedAt) SELECT songId, playedAt FROM play_history;
DROP TABLE play_history;
ALTER TABLE play_history_new RENAME TO play_history;

CREATE TABLE downloads_new (songId TEXT NOT NULL, filePath TEXT NOT NULL, mimeType TEXT, downloadedAt INTEGER NOT NULL, PRIMARY KEY(songId), FOREIGN KEY(songId) REFERENCES songs(songId) ON UPDATE NO ACTION ON DELETE CASCADE);
INSERT INTO downloads_new (songId, filePath, mimeType, downloadedAt) SELECT songId, filePath, mimeType, downloadedAt FROM downloads;
DROP TABLE downloads;
ALTER TABLE downloads_new RENAME TO downloads;
COMMIT;

PRAGMA foreign_keys = ON;

-- ---------- assertions ----------
.print === data preserved / orphans removed
SELECT CASE WHEN COUNT(*)=2 THEN 'ok  ' ELSE 'FAIL' END || ' playlist_songs kept 2 valid rows, dropped 2 orphans' FROM playlist_songs;
SELECT CASE WHEN COUNT(*)=1 THEN 'ok  ' ELSE 'FAIL' END || ' liked_songs kept 1 row' FROM liked_songs;
SELECT CASE WHEN COUNT(*)=1 THEN 'ok  ' ELSE 'FAIL' END || ' play_history kept 1 row' FROM play_history;
SELECT CASE WHEN COUNT(*)=1 THEN 'ok  ' ELSE 'FAIL' END || ' downloads kept 1 row' FROM downloads;
SELECT CASE WHEN position=1 THEN 'ok  ' ELSE 'FAIL' END || ' playlist position survived the copy' FROM playlist_songs WHERE songId='s2';
SELECT CASE WHEN filePath='/tmp/a.m4a' THEN 'ok  ' ELSE 'FAIL' END || ' download filePath survived the copy' FROM downloads WHERE songId='s1';

.print
.print === resulting schema (compare against app/schemas/.../5.json)
SELECT sql FROM sqlite_master WHERE name IN ('playlist_songs','liked_songs','play_history','downloads') AND type='table';
.print --- indices
SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'index_%' ORDER BY name;

.print
.print === foreign keys registered
SELECT 'playlist_songs -> ' || "table" || '(' || "to" || ') ON DELETE ' || on_delete FROM pragma_foreign_key_list('playlist_songs');
SELECT 'liked_songs    -> ' || "table" || '(' || "to" || ') ON DELETE ' || on_delete FROM pragma_foreign_key_list('liked_songs');
SELECT 'play_history   -> ' || "table" || '(' || "to" || ') ON DELETE ' || on_delete FROM pragma_foreign_key_list('play_history');
SELECT 'downloads      -> ' || "table" || '(' || "to" || ') ON DELETE ' || on_delete FROM pragma_foreign_key_list('downloads');

.print
.print === cascade behaviour
DELETE FROM playlists WHERE playlistId = 1;
SELECT CASE WHEN COUNT(*)=0 THEN 'ok  ' ELSE 'FAIL' END || ' deleting a playlist cascaded to playlist_songs' FROM playlist_songs;
DELETE FROM songs WHERE songId = 's1';
SELECT CASE WHEN COUNT(*)=0 THEN 'ok  ' ELSE 'FAIL' END || ' deleting a song cascaded to liked_songs' FROM liked_songs;
SELECT CASE WHEN COUNT(*)=0 THEN 'ok  ' ELSE 'FAIL' END || ' deleting a song cascaded to downloads' FROM downloads;

.print
.print === constraint enforced
.print expected: the next statement fails with "FOREIGN KEY constraint failed"
.bail on
INSERT INTO liked_songs VALUES ('nope', 1);
.print 'FAIL - orphan like was accepted'
