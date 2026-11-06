package org.schabi.newpipe.fragments.detail;

import org.schabi.newpipe.fragments.detail.tabs.DetailTab;
import org.schabi.newpipe.fragments.detail.tabs.DetailTabsAdapter;
import org.schabi.newpipe.fragments.detail.tabs.TabSpec;
import org.schabi.newpipe.fragments.list.sponsorblock.SponsorBlockFragment;

import java.lang.reflect.Field;
import java.util.Collections;

/** Installs controlled editor pages through the production ViewPager2 adapter. */
public final class DetailTestState {
    private DetailTestState() {
    }

    public static VideoDetailViewModel model(final VideoDetailFragment fragment) {
        try {
            final Field field = VideoDetailFragment.class.getDeclaredField("vm");
            field.setAccessible(true);
            VideoDetailViewModel model = (VideoDetailViewModel) field.get(fragment);
            if (model == null) {
                model = new VideoDetailViewModel();
                field.set(fragment, model);
            }
            return model;
        } catch (final ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    public static void showEditor(final VideoDetailFragment fragment,
                                  final SponsorBlockFragment editor, final long itemId) {
        try {
            final Field tabsField = VideoDetailFragment.class.getDeclaredField("tabs");
            tabsField.setAccessible(true);
            final Object tabs = tabsField.get(fragment);
            final Field adapterField = tabs.getClass().getDeclaredField("adapter");
            adapterField.setAccessible(true);
            final DetailTabsAdapter adapter = (DetailTabsAdapter) adapterField.get(tabs);
            final TabSpec spec = new TabSpec(DetailTab.SPONSOR_BLOCK, itemId, () -> editor);
            model(fragment).getTabsState().setTabs(Collections.singletonList(spec));
            model(fragment).getTabsState().setPreferredTag(DetailTab.SPONSOR_BLOCK.getTag());
            adapter.submit(Collections.singletonList(spec));
        } catch (final ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
}
