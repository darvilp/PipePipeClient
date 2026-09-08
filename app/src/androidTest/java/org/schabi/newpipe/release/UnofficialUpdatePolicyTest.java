package org.schabi.newpipe.release;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.NewVersionWorker;

import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class UnofficialUpdatePolicyTest {
    @Test
    public void updateEntryPointCancelsPreviouslyScheduledOfficialWork() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final WorkManager manager = WorkManager.getInstance(context);
        final OneTimeWorkRequest legacy = new OneTimeWorkRequest.Builder(NewVersionWorker.class)
                .setInitialDelay(1, TimeUnit.DAYS).build();
        manager.enqueue(legacy).getResult().get(10, TimeUnit.SECONDS);
        try {
            assertEquals(WorkInfo.State.ENQUEUED,
                    manager.getWorkInfoById(legacy.getId()).get(10, TimeUnit.SECONDS).getState());
            NewVersionWorker.enqueueNewVersionCheckingWork(context, false);
            WorkInfo.State state = WorkInfo.State.ENQUEUED;
            final long deadline = SystemClock.uptimeMillis() + 3000;
            while (state == WorkInfo.State.ENQUEUED && SystemClock.uptimeMillis() < deadline) {
                state = manager.getWorkInfoById(legacy.getId()).get(10, TimeUnit.SECONDS).getState();
                if (state == WorkInfo.State.ENQUEUED) {
                    SystemClock.sleep(25);
                }
            }
            assertEquals(WorkInfo.State.CANCELLED, state);
        } finally {
            manager.cancelAllWorkByTag(NewVersionWorker.class.getName())
                    .getResult().get(10, TimeUnit.SECONDS);
        }
    }
}
