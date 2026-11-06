package org.schabi.newpipe.release;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.schabi.newpipe.BuildConfig;

/** Explicit two-install probe using framework instrumentation to avoid minified-library conflicts. */
public class UnofficialReleaseUpgradeProbe extends Instrumentation {
    private String phase;

    @Override
    public void onCreate(final Bundle arguments) {
        super.onCreate(arguments);
        phase = arguments.getString("releaseUpgradePhase", "");
        start();
    }

    @Override
    public void onStart() {
        final Bundle result = new Bundle();
        try {
            if ("seed".equals(phase)) {
                seedOlderInstallation();
            } else if ("verify".equals(phase)) {
                signedReleasePreservesOlderInstallation();
            } else {
                throw new IllegalArgumentException("Specify releaseUpgradePhase=seed or verify");
            }
            result.putString("stream", "Release upgrade " + phase + ": PASS\n");
            finish(Activity.RESULT_OK, result);
        } catch (Exception | AssertionError failure) {
            result.putString("stream", "Release upgrade " + phase + ": FAIL " + failure + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private static void assertTrue(final String message, final boolean condition) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertTrue(final boolean condition) {
        assertTrue("Expected true", condition);
    }

    private static void assertEquals(final Object expected, final Object actual) {
        assertTrue("Expected " + expected + ", got " + actual, expected.equals(actual));
    }

    private static final String CHANNEL_URL = "https://example.invalid/release-upgrade-channel";
    private static final String PLAYLIST_NAME = "Release upgrade acceptance fixture";

    private Context targetContext() {
        final Context context = getTargetContext();
        assertEquals("InfinityLoop1309.NewPipeEnhanced.debug.allfeatures", context.getPackageName());
        return context;
    }

    private SharedPreferences preferences(final Context context) {
        return context.getSharedPreferences(context.getPackageName() + "_preferences",
                Context.MODE_PRIVATE);
    }

    public void seedOlderInstallation() {
        final Context context = targetContext();
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

    public void signedReleasePreservesOlderInstallation() throws Exception {
        final Context context = targetContext();
        assertTrue("Must exercise the actual release variant",
                (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0);
        assertEquals(BuildConfig.VERSION_NAME, context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0).versionName);
        assertTrue("The older-install phase must run before upgrading",
                preferences(context).getBoolean("release_upgrade_fixture", false));
        assertEquals(1.25f, preferences(context).getFloat("playback_speed_key", 0f));
        assertEquals(0.9f, preferences(context).getFloat("playback_pitch_key", 0f));
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
            assertEquals(42L, subscription.getLong(1));
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
