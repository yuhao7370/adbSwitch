package com.elleng.adbswitch;

import org.junit.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class QuickLaunchTransactionTest {
    @Test public void launchesWhileDebugAndRearAreOffBeforeReturningToTheQueue() {
        Device device = new Device();
        QuickLaunchTransaction.Result result = QuickLaunchTransaction.run(device);
        assertNull(result.error);
        assertTrue(device.running);
        assertFalse(device.debug.usb);
        assertFalse(device.rearEnabled);
        assertEquals(Arrays.asList("read", "close", "remember", "rear-off", "launch"), device.events);
        // A queued restore executes only after launch, even if the UI callback is delayed.
        device.restoreRear();
        assertFalse(device.running);
        assertTrue(device.rearEnabled);
    }

    @Test public void failedDebugCloseNeverLaunchesAndRestoresTheOriginalCombination() {
        Device device = new Device();
        device.failClose = true;
        QuickLaunchTransaction.Result result = QuickLaunchTransaction.run(device);
        assertNotNull(result.error);
        assertFalse(device.events.contains("launch"));
        assertTrue(device.debug.usb);
        assertFalse(device.debug.wireless);
        assertTrue(device.rearEnabled);
    }

    @Test public void launchFailureRollsBackBeforeReturningToTheQueue() {
        Device device = new Device();
        device.failLaunch = true;
        QuickLaunchTransaction.Result result = QuickLaunchTransaction.run(device);
        assertNotNull(result.error);
        assertFalse(device.running);
        assertTrue(device.rearEnabled);
        assertTrue(device.debug.usb);
        assertEquals(Arrays.asList("read", "close", "remember", "rear-off", "launch",
                "restore-rear", "restore-debug"), device.events);
    }

    @Test public void failedRollbackReadbackNeverClaimsTheOldDebugStateWasConfirmed() {
        Device device = new Device();
        device.failRear = true;
        device.missingRollbackReadback = true;
        QuickLaunchTransaction.Result result = QuickLaunchTransaction.run(device);
        assertNotNull(result.error);
        assertNull(result.after);
        assertFalse(device.debug.anyOn());
        assertFalse(device.running);
    }

    @Test public void aRearRecoveryFailureDoesNotPreventAttemptingDebugRecovery() {
        Device device = new Device();
        device.failRear = true;
        device.failRearRecovery = true;
        QuickLaunchTransaction.Result result = QuickLaunchTransaction.run(device);
        assertNotNull(result.error);
        assertTrue(device.debug.usb);
        assertFalse(device.running);
    }

    @Test public void missingInitialReadbackDoesNotChangeAnything() {
        Device device = new Device();
        device.missingInitialReadback = true;
        QuickLaunchTransaction.Result result = QuickLaunchTransaction.run(device);
        assertNotNull(result.error);
        assertNull(result.after);
        assertEquals(Arrays.asList("read"), device.events);
        assertTrue(device.debug.usb);
        assertTrue(device.rearEnabled);
    }

    private static final class Device implements QuickLaunchTransaction.Operations {
        DebugController.State debug = new DebugController.State(true, false, true);
        boolean rearEnabled = true, running;
        boolean failClose, failRear, failLaunch, missingRollbackReadback, missingInitialReadback, failRearRecovery;
        final List<String> events = new ArrayList<>();

        public DebugController.State readDebug() {
            events.add("read");
            return missingInitialReadback ? null : debug;
        }
        public DebugController.State closeDebug() throws Exception {
            events.add("close");
            debug = new DebugController.State(false, false, true);
            if (failClose) throw new IOException("close failed");
            return debug;
        }
        public void remember(DebugController.State before) { events.add("remember"); }
        public void prepareRear() throws Exception {
            if (running) restoreRear();
            events.add("rear-off");
            rearEnabled = false;
            if (failRear) throw new IOException("display failure");
        }
        public void launch() throws Exception {
            events.add("launch");
            if (debug.anyOn() || rearEnabled) throw new AssertionError("Unsafe target launch");
            if (failLaunch) throw new IOException("target no longer available");
            running = true;
        }
        public void restoreRear() {
            events.add("restore-rear");
            if (failRearRecovery) throw new IllegalStateException("wake failed");
            running = false;
            rearEnabled = true;
        }
        public DebugController.State restoreDebug(DebugController.State before) {
            events.add("restore-debug");
            if (missingRollbackReadback) return null;
            debug = before;
            return debug;
        }
    }
}
