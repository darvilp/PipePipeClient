package org.schabi.newpipe.database

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.Migrations.MIGRATION_901_902

@RunWith(AndroidJUnit4::class)
class FeedContentMigrationTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun deleteDatabase() {
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrationAddsContentSelectionsWithoutLosingExistingData() {
        migrationHelper.createDatabase(TEST_DATABASE, 901).use { database ->
            database.execSQL(
                "INSERT INTO subscriptions " +
                    "(uid, service_id, url, name, notification_mode) VALUES " +
                    "(1, 0, 'channel', 'Channel', 0)"
            )
            database.execSQL(
                "INSERT INTO feed_group " +
                    "(uid, name, icon_id, sort_order) VALUES (1, 'Group', 0, 0)"
            )
            database.execSQL(
                "INSERT INTO feed_group_subscription_join " +
                    "(group_id, subscription_id) VALUES (1, 1)"
            )

            insertStream(database, 1, "VIDEO_STREAM")
            insertStream(database, 2, "LIVE_STREAM")
            insertStream(database, 3, "POST_LIVE_STREAM")
            database.execSQL(
                "INSERT INTO feed (stream_id, subscription_id) VALUES " +
                    "(1, 1), (2, 1), (3, 1)"
            )
        }

        val database = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            902,
            true,
            MIGRATION_901_902
        )

        database.query(
            "SELECT content_selection FROM feed_group WHERE uid = 1"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(7, cursor.getInt(0))
        }
        database.query(
            "SELECT content_selection_override FROM feed_group_subscription_join " +
                "WHERE group_id = 1 AND subscription_id = 1"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(true, cursor.isNull(0))
        }
        database.query(
            "SELECT stream_id, content_selection FROM feed ORDER BY stream_id"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getLong(0))
            assertEquals(1, cursor.getInt(1))
            cursor.moveToNext()
            assertEquals(2, cursor.getLong(0))
            assertEquals(4, cursor.getInt(1))
            cursor.moveToNext()
            assertEquals(3, cursor.getLong(0))
            assertEquals(4, cursor.getInt(1))
        }
    }

    private fun insertStream(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        id: Long,
        streamType: String
    ) {
        database.execSQL(
            "INSERT INTO streams " +
                "(uid, service_id, url, title, stream_type, duration, uploader, is_paid) " +
                "VALUES ($id, 0, 'stream$id', 'Stream $id', '$streamType', 60, " +
                "'Channel', 0)"
        )
    }

    companion object {
        private const val TEST_DATABASE = "feed-content-migration-test"
    }
}
