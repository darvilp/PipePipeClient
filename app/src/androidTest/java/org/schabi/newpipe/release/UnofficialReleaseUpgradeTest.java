package org.schabi.newpipe.release;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Explicit two-install acceptance probe. Skipped during ordinary regression runs. */
@RunWith(AndroidJUnit4.class)
public class UnofficialReleaseUpgradeTest {
    private static final String CHANNEL_URL = "https://example.invalid/release-upgrade-channel";
    private static final String PLAYLIST_NAME = "Release upgrade acceptance fixture";

    private Context contextForPhase(final String phase) {
        Assume.assumeTrue(phase.equals(InstrumentationRegistry.getArguments()
                .getString("releaseUpgradePhase")));
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("InfinityLoop1309.NewPipeEnhanced.debug.allfeatures", context.getPackageName());
        return context;
    }

    private SharedPreferences preferences(final Context context) {
        return context.getSharedPreferences(context.getPackageName() + "_preferences",
                Context.MODE_PRIVATE);
    }

    @Test
    public void seedOlderInstallation() {
        final Context context = contextForPhase("seed");
        assertTrue("Launch the older app first", context.getDatabasePath("newpipe.db").exists());
        try (SQLiteDatabase database = SQLiteDatabase.openDatabase(
                context.getDatabasePath("newpipe.db").getPath(), null, SQLiteDatabase.OPEN_READWRITE)) {
            database.execSQL("INSERT OR IGNORE INTO subscriptions "
                    + "(service_id, url, name, subscriber_count, notification_mode) "
                    + "VALUES (0, ?, 'Release upgrade channel', 42, 0)", new Object[]{CHANNEL_URL});
            database.execSQL("INSERT INTO playlists (name, thumbnail_url, display_index) "
                    + "SELECT ?, '', 0 WHERE NOT EXISTS (SELECT 1 FROM playlists WHERE name = ?)",
                    new Object[]{PLAYLIST_NAME, PLAYLIST_NAME});
        }
        assertTrue(preferences(context).edit().putFloat("playback_speed_key", 1.25f)
                .putFloat("playback_pitch_key", 0.9f).putString("theme", "dark_theme")
                .putBoolean("release_upgrade_fixture", true).commit());
    }

    @Test
    public void signedReleasePreservesOlderInstallation() throws Exception {
        final Context context = contextForPhase("verify");
        assertFalse("Must exercise the actual release variant",
                (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
        assertEquals("5.3.1-beta-unofficial.1", context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0).versionName);
        assertTrue("The older-install phase must run before upgrading",
                preferences(context).getBoolean("release_upgrade_fixture", false));
        assertEquals(1.25f, preferences(context).getFloat("playback_speed_key", 0f), 0f);
        assertEquals(0.9f, preferences(context).getFloat("playback_pitch_key", 0f), 0f);
        assertEquals("dark_theme", preferences(context).getString("theme", ""));
        try (SQLiteDatabase database = SQLiteDatabase.openDatabase(
                context.getDatabasePath("newpipe.db").getPath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor subscription = database.rawQuery("SELECT name, subscriber_count FROM "
                     + "subscriptions WHERE service_id = 0 AND url = ?", new String[]{CHANNEL_URL});
             Cursor playlist = database.rawQuery("SELECT name FROM playlists WHERE name = ?",
                     new String[]{PLAYLIST_NAME})) {
            assertEquals(1, subscription.getCount());
            assertTrue(subscription.moveToFirst());
            assertEquals("Release upgrade channel", subscription.getString(0));
            assertEquals(42, subscription.getLong(1));
            assertEquals(1, playlist.getCount());
        }
        final Class<?> worker = Class.forName("org.schabi.newpipe.NewVersionWorker", true,
                context.getClassLoader());
        assertTrue("Legacy WorkManager reflection constructor must survive R8",
                java.util.Arrays.stream(worker.getConstructors()).anyMatch(constructor ->
                        constructor.getParameterCount() == 2
                                && constructor.getParameterTypes()[0] == Context.class));
    }
}
