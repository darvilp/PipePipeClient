package org.schabi.newpipe.local.feed

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xwray.groupie.GroupieAdapter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.feed.item.StreamItem

@RunWith(AndroidJUnit4::class)
class FeedRefreshRenderingTest {
    @Test
    fun changedFilterSnapshotReplacesNonemptyRowsDuringProgress() = withFeed { scenario, fragment ->
        val cache = FeedRefreshStateCache()
        val played = item(1, 1000)
        val unplayed = item(2, null)
        scenario.onActivity {
            fragment.handleResult(cache.remember(loaded(played, unplayed), true))
        }
        awaitRows(scenario, fragment, listOf(1L, 2L))

        scenario.onActivity {
            cache.remember(loaded(unplayed), false)
            fragment.handleResult(cache.progress(1, 10, 0))
        }
        awaitRows(scenario, fragment, listOf(2L))

        scenario.onActivity { fragment.handleResult(cache.progress(2, 10, 0)) }
        awaitRows(scenario, fragment, listOf(2L))
    }

    @Test
    fun emptyFilteredSnapshotClearsRowsAndCanReturnToAllItems() = withFeed { scenario, fragment ->
        val cache = FeedRefreshStateCache()
        val played = item(1, 1000)
        scenario.onActivity { fragment.handleResult(cache.remember(loaded(played), true)) }
        awaitRows(scenario, fragment, listOf(1L))

        scenario.onActivity {
            cache.remember(loaded(), false)
            fragment.handleResult(cache.progress(1, 10, 0))
        }
        awaitRows(scenario, fragment, emptyList())

        scenario.onActivity {
            cache.remember(loaded(played), true)
            fragment.handleResult(cache.progress(2, 10, 0))
        }
        awaitRows(scenario, fragment, listOf(1L))
    }

    @Test
    fun errorAppliesTheCurrentFilterIncludingAnEmptyResult() = withFeed { scenario, fragment ->
        val cache = FeedRefreshStateCache()
        val played = item(1, 1000)
        val unplayed = item(2, null)
        scenario.onActivity { fragment.handleResult(cache.remember(loaded(played, unplayed), true)) }
        awaitRows(scenario, fragment, listOf(1L, 2L))

        scenario.onActivity {
            cache.remember(loaded(unplayed), false)
            fragment.handleResult(cache.error(IllegalStateException("Refresh fixture")))
        }
        awaitRows(scenario, fragment, listOf(2L))

        scenario.onActivity {
            cache.remember(loaded(), false)
            fragment.handleResult(cache.error(IllegalStateException("Empty refresh fixture")))
        }
        awaitRows(scenario, fragment, emptyList())
    }

    private fun withFeed(check: (ActivityScenario<MainActivity>, FeedFragment) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val fragment = FeedFragment.newInstance()
            scenario.onActivity { activity ->
                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_holder, fragment)
                    .commitNow()
                val model = field(fragment, "viewModel") as FeedViewModel
                model.stateLiveData.removeObservers(fragment.viewLifecycleOwner)
            }
            check(scenario, fragment)
        }
    }

    private fun awaitRows(
        scenario: ActivityScenario<MainActivity>,
        fragment: FeedFragment,
        expected: List<Long>
    ) {
        val deadline = SystemClock.uptimeMillis() + 5000
        var actual = emptyList<Long>()
        do {
            scenario.onActivity {
                val adapter = field(fragment, "groupAdapter") as GroupieAdapter
                actual = (0 until adapter.itemCount).map { position ->
                    (adapter.getItem(position) as StreamItem).streamWithState.stream.uid
                }
            }
            if (actual == expected) return
            SystemClock.sleep(20)
        } while (SystemClock.uptimeMillis() < deadline)
        assertEquals(expected, actual)
    }

    private fun field(fragment: FeedFragment, name: String): Any =
        FeedFragment::class.java.getDeclaredField(name).apply { isAccessible = true }.get(fragment)

    private fun loaded(vararg items: StreamItem) = FeedState.LoadedState(
        items = items.toList(), notLoadedCount = 0
    )

    private fun item(id: Long, progress: Long?) = StreamItem(
        StreamWithState(
            StreamEntity(
                uid = id, serviceId = 0, url = "https://example.invalid/feed/$id",
                title = "Feed fixture $id", streamType = StreamType.VIDEO_STREAM,
                duration = 60, uploader = "Fixture"
            ),
            progress
        )
    )
}
