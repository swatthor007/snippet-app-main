package com.sockjaw.clipsy.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Snippet::class, UsageEvent::class, Folder::class, LinkPreview::class], version = 7, exportSchema = false)
abstract class ClipsyDatabase : RoomDatabase() {
    abstract fun snippetDao(): SnippetDao
    abstract fun usageDao(): UsageDao
    abstract fun folderDao(): FolderDao
    abstract fun linkPreviewDao(): LinkPreviewDao

    companion object {
        fun create(context: Context): ClipsyDatabase =
            Room.databaseBuilder(context, ClipsyDatabase::class.java, "clipsy.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()

        /** FILE split into MEDIA (images, GIFs) and DOCUMENT (everything else). No schema change. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE snippets SET type = 'MEDIA' WHERE type = 'FILE' AND mimeType LIKE 'image/%'")
                db.execSQL("UPDATE snippets SET type = 'DOCUMENT' WHERE type = 'FILE'")
            }
        }

        /** Folders, and the folder a snippet belongs to. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `folders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("ALTER TABLE `snippets` ADD COLUMN `folderId` INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_snippets_folderId` ON `snippets` (`folderId`)")
            }
        }

        /** A colour and an icon for each folder. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `folders` ADD COLUMN `color` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `folders` ADD COLUMN `icon` TEXT NOT NULL DEFAULT 'folder'")
            }
        }

        /** A per-snippet number for {counter}. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `snippets` ADD COLUMN `counter` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** What a saved link points at: title, description and share image. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `link_previews` (`url` TEXT NOT NULL, `kind` TEXT NOT NULL, `title` TEXT, `description` TEXT, " +
                        "`siteName` TEXT, `imageUrl` TEXT, `imagePath` TEXT, `mimeType` TEXT, `sizeBytes` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
                        "`fetchedAt` INTEGER NOT NULL, `keepAsLink` INTEGER NOT NULL, PRIMARY KEY(`url`))",
                )
            }
        }

        /** Locked (encrypted) text snippets. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `snippets` ADD COLUMN `locked` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `snippets` ADD COLUMN `sealedContent` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `snippets` ADD COLUMN `expandUnlocked` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `snippets` ADD COLUMN `openSeal` TEXT DEFAULT NULL")
            }
        }
    }
}
