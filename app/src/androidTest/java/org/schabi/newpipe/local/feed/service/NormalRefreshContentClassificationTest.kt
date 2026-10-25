package org.schabi.newpipe.local.feed.service

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedContentSelection
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.CancellableCall
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Runs normal extraction and persistence; only the PeerTube HTTP responses are controlled. */
@RunWith(AndroidJUnit4::class)
class NormalRefreshContentClassificationTest {
    @Test
    fun normalPeerTubeRefreshKeepsLiveItemsInLiveOnlyGroup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = NewPipeDatabase.getInstance(context)
        val sqlite = database.openHelper.writableDatabase
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val fastKey = context.getString(R.string.feed_use_dedicated_fetch_method_key)
        val tabsKey = context.getString(R.string.feed_fetch_channel_tabs_key)
        val oldFast = preferences.all[fastKey] as Boolean?
        val oldTabs = preferences.getStringSet(tabsKey, null)?.toSet()
        val oldDownloader = NewPipe.getDownloader()
        val localization = NewPipe.getPreferredLocalization()
        val country = NewPipe.getPreferredContentCountry()
        val base = "https://feed-regression.invalid"
        val channel = "$base/c/live"
        val service = ServiceList.PeerTube.serviceId
        var subscription = -1L
        val names = listOf("Review live fixture", "Review videos fixture")
        try {
            preferences.edit().putBoolean(fastKey, false).remove(tabsKey).commit()
            sqlite.execSQL(
                "INSERT INTO subscriptions (service_id, url, name, notification_mode) " +
                    "VALUES (?, ?, 'Review PeerTube fixture', 0)", arrayOf<Any>(service, channel)
            )
            sqlite.query("SELECT uid FROM subscriptions WHERE url = ?", arrayOf(channel)).use {
                assertTrue(it.moveToFirst())
                subscription = it.getLong(0)
            }
            val manager = FeedDatabaseManager(context)
            for ((name, selection) in names.zip(
                listOf(FeedContentSelection.LIVE, FeedContentSelection.VIDEOS)
            )) {
                assertTrue(manager.createGroupWithContentRules(name, FeedGroupIcon.ALL,
                    selection, setOf(subscription), emptyMap()).blockingAwait(10, TimeUnit.SECONDS))
            }
            val groupIds = names.map { name ->
                sqlite.query("SELECT uid FROM feed_group WHERE name = ?", arrayOf(name)).use {
                    assertTrue(it.moveToFirst())
                    it.getLong(0)
                }
            }
            val owner = """{"name":"owner","host":"feed-regression.invalid",
                "displayName":"Fixture owner","url":"$base/a/owner","avatars":[]}"""
            val published = Instant.now().minusSeconds(3600).toString()
            fun video(id: Int, live: Boolean) = """{
                "uuid":"00000000-0000-4000-8000-00000000000$id",
                "name":"${if (live) "Live fixture" else "Video fixture"}",
                "isLive":$live,"duration":60,"publishedAt":"$published",
                "views":1,"thumbnailPath":"/thumbnail.jpg","account":$owner
            }"""
            val channelJson = """{"displayName":"Fixture channel","name":"live",
                "description":"Controlled channel","followersCount":1,"avatars":[],
                "banners":[],"ownerAccount":$owner}"""
            val videosJson = """{"total":2,"data":[${video(1, true)},${video(2, false)}]}"""
            NewPipe.init(object : Downloader() {
                override fun execute(request: Request): Response {
                    val url = request.url()
                    if (!url.startsWith("$base/api/v1/")) throw IOException("Unexpected fixture URL")
                    val body = if (url.contains("/videos?")) videosJson else channelJson
                    return Response(200, "OK", emptyMap(), body, body.toByteArray(), url)
                }
                override fun executeAsync(request: Request, callback: AsyncCallback): CancellableCall {
                    throw UnsupportedOperationException("Normal PeerTube extraction is synchronous")
                }
            }, localization, country)
            val results = FeedLoadManager(context).startLoading(groupIds[0], true)
                .timeout(30, TimeUnit.SECONDS).blockingGet()
            assertEquals(1, results.size)
            assertTrue("Normal refresh must finish without an extraction error", results[0].isOnNext)
            assertTrue("Controlled extraction must have no parser errors", results[0].value!!.errors.isEmpty())
            assertEquals(2, results[0].value!!.streams.size)
            assertEquals("Live-only group must retain the extracted live entry",
                listOf("Live fixture"), manager.getStreams(groupIds[0]).blockingGet(emptyList())
                    .map { it.stream.title })
            assertEquals("Videos-only group must exclude the extracted live entry",
                listOf("Video fixture"), manager.getStreams(groupIds[1]).blockingGet(emptyList())
                    .map { it.stream.title })
        } finally {
            NewPipe.init(oldDownloader, localization, country)
            names.forEach { sqlite.execSQL("DELETE FROM feed_group WHERE name = ?", arrayOf(it)) }
            if (subscription >= 0) sqlite.execSQL("DELETE FROM subscriptions WHERE uid = ?", arrayOf(subscription))
            preferences.edit().apply {
                if (oldFast == null) remove(fastKey) else putBoolean(fastKey, oldFast)
                if (oldTabs == null) remove(tabsKey) else putStringSet(tabsKey, oldTabs)
            }.commit()
        }
    }
}
