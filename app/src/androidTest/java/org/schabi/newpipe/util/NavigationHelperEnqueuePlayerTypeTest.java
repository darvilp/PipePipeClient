package org.schabi.newpipe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.schabi.newpipe.player.PlayerService.PlayerType.AUDIO;
import static org.schabi.newpipe.player.PlayerService.PlayerType.POPUP;
import static org.schabi.newpipe.player.PlayerService.PlayerType.VIDEO;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class NavigationHelperEnqueuePlayerTypeTest {
    @Test
    public void activeQueueKeepsCurrentPlayerType() {
        assertEquals(VIDEO, NavigationHelper.resolveEnqueuePlayerType(AUDIO, VIDEO, true));
        assertEquals(AUDIO, NavigationHelper.resolveEnqueuePlayerType(POPUP, AUDIO, true));
        assertEquals(POPUP, NavigationHelper.resolveEnqueuePlayerType(VIDEO, POPUP, true));
    }

    @Test
    public void missingQueueUsesRequestedFallbackPlayerType() {
        assertEquals(VIDEO, NavigationHelper.resolveEnqueuePlayerType(VIDEO, AUDIO, false));
        assertEquals(AUDIO, NavigationHelper.resolveEnqueuePlayerType(AUDIO, POPUP, false));
        assertEquals(POPUP, NavigationHelper.resolveEnqueuePlayerType(POPUP, VIDEO, false));
    }

    @Test
    public void missingCurrentTypeUsesRequestedFallbackPlayerType() {
        assertEquals(VIDEO, NavigationHelper.resolveEnqueuePlayerType(VIDEO, null, true));
    }

    @Test
    public void popupPermissionIsOnlyRequiredWhenStartingPopupPlayback() {
        assertFalse(NavigationHelper.shouldRequirePopupPermission(POPUP, true));
        assertFalse(NavigationHelper.shouldRequirePopupPermission(VIDEO, false));
        assertTrue(NavigationHelper.shouldRequirePopupPermission(POPUP, false));
    }
}
