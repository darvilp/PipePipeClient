package org.schabi.newpipe.fragments.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem

class ChatAdapterDiffTest {
    @Test
    fun `chat entries retain reference identity even when messages repeat`() {
        val first = BulletCommentsInfoItem(0, "https://example.invalid/chat", "message").apply {
            commentText = "repeated message"
        }
        val repeated = BulletCommentsInfoItem(0, first.url, first.name).apply {
            commentText = first.commentText
        }

        assertTrue(ChatAdapter.Diff.areItemsTheSame(first, first))
        assertTrue(ChatAdapter.Diff.areContentsTheSame(first, first))
        assertFalse(ChatAdapter.Diff.areItemsTheSame(first, repeated))
        assertFalse(ChatAdapter.Diff.areContentsTheSame(first, repeated))
    }
}
