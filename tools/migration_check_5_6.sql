-- Replays the Room v5 -> v6 migration against real SQLite and asserts the outcome.
-- Run with: sqlite3.exe :memory: ".read migration_check_5_6.sql"

PRAGMA foreign_keys = ON;

-- ---------- v5 schema, exactly as migrations 1..5 leave it ----------
CREATE TABLE songs (songId TEXT NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, album TEXT, artworkUrl TEXT, durationMs INTEGER NOT NULL, PRIMARY KEY(songId));
CREATE TABLE play_history (songId TEXT NOT NULL, playedAt INTEGER NOT NULL, PRIMARY KEY(songId), FOREIGN KEY(songId) REFERENCES songs(songId) ON UPDATE NO ACTION ON DELETE CASCADE);

INSERT INTO songs VALUES ('s1','A','Artist One',NULL,NULL,180000);
INSERT INTO songs VALUES ('s2','B','Artist Two',NULL,NULL,240000);
INSERT INTO play_history VALUES ('s1', 1700000000000);
INSERT INTO play_history VALUES ('s2', 1700000100000);

-- ---------- MIGRATION_5_6, transcribed from DatabaseModule ----------
BEGIN;
CREATE TABLE IF NOT EXISTS `play_events` (`eventId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `songId` TEXT NOT NULL, `playedAt` INTEGER NOT NULL, FOREIGN KEY(`songId`) REFERENCES `songs`(`songId`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE INDEX IF NOT EXISTS `index_play_events_songId` ON `play_events` (`songId`);
CREATE INDEX IF NOT EXISTS `index_play_events_playedAt` ON `play_events` (`playedAt`);
INSERT INTO play_events (songId, playedAt) SELECT songId, playedAt FROM play_history WHERE songId IN (SELECT songId FROM songs);
COMMIT;

-- ---------- assertions ----------
.print === seeding
SELECT CASE WHEN COUNT(*)=2 THEN 'ok  ' ELSE 'FAIL' END || ' existing play_history seeded into play_events' FROM play_events;
SELECT CASE WHEN playedAt=1700000000000 THEN 'ok  ' ELSE 'FAIL' END || ' playedAt survived the copy' FROM play_events WHERE songId='s1';
SELECT CASE WHEN COUNT(*)=2 THEN 'ok  ' ELSE 'FAIL' END || ' play_history left untouched' FROM play_history;

.print
.print === play counts are now possible (the whole point of the table)
INSERT INTO play_events (songId, playedAt) VALUES ('s1', 1700000200000);
INSERT INTO play_events (songId, playedAt) VALUES ('s1', 1700000300000);
SELECT CASE WHEN playCount=3 THEN 'ok  ' ELSE 'FAIL' END || ' s1 counted ' || playCount || ' plays'
FROM (SELECT COUNT(*) AS playCount FROM play_events WHERE songId='s1');

.print
.print === rowid alias / AUTOINCREMENT
SELECT CASE WHEN COUNT(*)=1 THEN 'ok  ' ELSE 'FAIL' END || ' sqlite_sequence row exists, so eventId autoincrements'
FROM sqlite_sequence WHERE name='play_events';
SELECT CASE WHEN MAX(eventId)=4 AND MIN(eventId)=1 THEN 'ok  ' ELSE 'FAIL' END || ' eventId assigned 1..4' FROM play_events;

.print
.print === aggregation queries used by StatsDao
-- 3 plays of s1 (180000 ms) + 1 of s2 (240000 ms).
SELECT CASE WHEN total=780000 THEN 'ok  ' ELSE 'FAIL' END || ' total listening ms = ' || total
FROM (SELECT COALESCE(SUM(s.durationMs),0) AS total FROM play_events e INNER JOIN songs s ON s.songId=e.songId);
SELECT CASE WHEN n=2 THEN 'ok  ' ELSE 'FAIL' END || ' distinct artists = ' || n
FROM (SELECT COUNT(DISTINCT s.artist) AS n FROM play_events e INNER JOIN songs s ON s.songId=e.songId WHERE TRIM(s.artist) != '');
SELECT CASE WHEN COUNT(*)>=1 THEN 'ok  ' ELSE 'FAIL' END || ' day bucketing returns rows'
FROM (SELECT strftime('%Y-%m-%d', playedAt/1000, 'unixepoch', 'localtime') AS day FROM play_events GROUP BY day);
SELECT CASE WHEN COUNT(*)>=1 THEN 'ok  ' ELSE 'FAIL' END || ' hour bucketing returns rows in 0..23'
FROM (SELECT CAST(strftime('%H', playedAt/1000, 'unixepoch', 'localtime') AS INTEGER) AS hour FROM play_events GROUP BY hour HAVING hour BETWEEN 0 AND 23);

.print
.print === resulting schema (compare against app/schemas/.../6.json)
SELECT sql FROM sqlite_master WHERE name='play_events' AND type='table';
SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'index_play_events%' ORDER BY name;
SELECT 'play_events -> ' || "table" || '(' || "to" || ') ON DELETE ' || on_delete FROM pragma_foreign_key_list('play_events');

.print
.print === cascade behaviour
DELETE FROM songs WHERE songId='s1';
SELECT CASE WHEN COUNT(*)=0 THEN 'ok  ' ELSE 'FAIL' END || ' deleting a song cascaded to play_events' FROM play_events WHERE songId='s1';
SELECT CASE WHEN COUNT(*)=1 THEN 'ok  ' ELSE 'FAIL' END || ' unrelated play_events rows survived' FROM play_events;

.print
.print === constraint enforced
.print expected: the next statement fails with "FOREIGN KEY constraint failed"
.bail on
INSERT INTO play_events (songId, playedAt) VALUES ('ghost', 1);
.print 'FAIL - orphan play event was accepted'
