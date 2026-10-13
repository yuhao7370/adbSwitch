package com.elleng.adbswitch;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class RearDisplaySessionTest {
    private static final String PKG = "hk.com.hsbc.hsbchkmobilebanking";
    private FakeHardware hardware;
    private FakeStore store;
    private RearDisplaySession session;

    @Before public void setUp() {
        hardware = new FakeHardware();
        store = new FakeStore();
        session = new RearDisplaySession(hardware, store);
    }

    @Test public void parsesRealLogicalDisplayFieldsWithoutUsingOverridePowerState() {
        String dump = "Logical Displays: size=2\n" + display(0, "local:main", true, "INTERNAL", false, "ON")
                + display(1, "local:rear", true, "INTERNAL", true, "OFF")
                + "    mOverrideDisplayInfo=DisplayInfo{displayId 1, state ON}\n";
        List<RearDisplaySession.Display> found = RearDisplaySession.parse(dump);
        assertEquals(2, found.size());
        assertEquals("local:rear", found.get(1).uniqueId);
        assertEquals("OFF", found.get(1).state);
        assertTrue(found.get(1).enabled);
        assertTrue(found.get(1).internal);
        assertTrue(found.get(1).presentation);
    }

    @Test public void persistsOwnershipBeforeDisablingOnlyTheRearDisplay() throws Exception {
        hardware.beforeDisable = () -> assertNotNull(store.pending);
        session.prepare(PKG);
        assertFalse(hardware.enabled);
        assertEquals("local:rear", store.pending.uniqueId);
        assertEquals(PKG, store.pending.packageName);
        assertEquals(Arrays.asList("disable:1"), hardware.commands);
    }

    @Test public void rejectsAmbiguousInternalDisplaysWithoutChangingHardware() {
        hardware.extra = display(2, "local:other", true, "INTERNAL", true, "ON");
        assertThrows(Exception.class, () -> session.prepare(PKG));
        assertNull(store.pending);
        assertTrue(hardware.commands.isEmpty());
    }

    @Test public void externalAndVirtualDisplaysAreNeverDisabled() throws Exception {
        hardware.extra = display(2, "hdmi:1", true, "EXTERNAL", true, "ON")
                + display(3, "virtual:1", true, "VIRTUAL", true, "ON");
        session.prepare(PKG);
        assertEquals(Arrays.asList("disable:1"), hardware.commands);
    }

    @Test public void absentOrMalformedDisplayDataFailsClosed() {
        hardware.overrideDump = "Display service unavailable\n";
        assertThrows(Exception.class, () -> session.prepare(PKG));
        assertTrue(hardware.commands.isEmpty());
    }

    @Test public void doesNotTakeOwnershipOfAnAlreadyDisabledRearDisplay() throws Exception {
        hardware.enabled = false;
        session.prepare(PKG);
        session.restore();
        assertNull(store.pending);
        assertTrue(hardware.commands.isEmpty());
    }

    @Test public void refusesHardwareChangesWhenRecoveryCannotBeSaved() {
        store.failSave = true;
        assertThrows(Exception.class, () -> session.prepare(PKG));
        assertTrue(hardware.commands.isEmpty());
    }

    @Test public void restoresWithWakeupAndStopsTheTargetBeforeEnabling() throws Exception {
        session.prepare(PKG);
        session.restore();
        assertEquals(Arrays.asList("disable:1", "stop:" + PKG, "enable:1", "wake:1"), hardware.commands);
        assertTrue(hardware.enabled);
        assertEquals("ON", hardware.state);
        assertNull(store.pending);
    }

    @Test public void usesStableIdentityIfTheNumericDisplayIdChanges() throws Exception {
        session.prepare(PKG);
        hardware.rearId = 4;
        session.restore();
        assertEquals(Arrays.asList("disable:1", "stop:" + PKG, "enable:4", "wake:4"), hardware.commands);
    }

    @Test public void neverEnablesAReplacementDisplayWithTheOldNumericId() throws Exception {
        session.prepare(PKG);
        hardware.uniqueId = "local:replacement";
        hardware.commands.clear();
        assertThrows(Exception.class, session::restore);
        assertTrue(hardware.commands.isEmpty());
        assertNotNull(store.pending);
    }

    @Test public void enableFailureKeepsRecoveryForRetry() throws Exception {
        session.prepare(PKG);
        hardware.failEnable = true;
        assertThrows(Exception.class, session::restore);
        assertNotNull(store.pending);
        assertFalse(hardware.enabled);
    }

    @Test public void wakeFailureKeepsRecoveryEvenWhenLogicalDisplayIsEnabled() throws Exception {
        session.prepare(PKG);
        hardware.failWake = true;
        assertThrows(Exception.class, session::restore);
        assertTrue(hardware.enabled);
        assertNotNull(store.pending);
        hardware.failWake = false;
        session.restore();
        assertNull(store.pending);
        assertEquals("ON", hardware.state);
    }

    @Test public void aSilentWakeFailureIsNotReportedAsRestored() throws Exception {
        session.prepare(PKG);
        hardware.silentWakeFailure = true;
        assertThrows(Exception.class, session::restore);
        assertNotNull(store.pending);
    }

    @Test public void partialDisableFailureIsRolledBack() {
        hardware.failAfterDisable = true;
        assertThrows(Exception.class, () -> session.prepare(PKG));
        assertTrue(hardware.enabled);
        assertEquals("ON", hardware.state);
        assertNull(store.pending);
    }

    @Test public void anotherAppProcessCanRecoverAnInterruptedSession() throws Exception {
        session.prepare(PKG);
        new RearDisplaySession(hardware, store).restore();
        assertTrue(hardware.enabled);
        assertNull(store.pending);
    }

    @Test public void rebootClearsOldOwnershipWithoutOverridingNewHardwareChoices() throws Exception {
        session.prepare(PKG);
        hardware.boot = "boot-two";
        hardware.commands.clear();
        new RearDisplaySession(hardware, store).restore();
        assertTrue(hardware.commands.isEmpty());
        assertNull(store.pending);
    }

    @Test public void repeatedLaunchDoesNotOverwriteTheRecoveryRecord() throws Exception {
        session.prepare(PKG);
        RearDisplaySession.Pending first = store.pending;
        session.prepare(PKG);
        assertSame(first, store.pending);
        assertEquals(Arrays.asList("disable:1"), hardware.commands);
    }

    @Test public void invalidPackageInputCannotReachShellOperations() {
        assertThrows(Exception.class, () -> session.prepare("a.b;reboot"));
        assertTrue(hardware.commands.isEmpty());
    }

    @Test public void debugOpeningStopsTheOwnedTargetWithoutWaitingForDisplayRecovery() throws Exception {
        session.prepare(PKG);
        hardware.uniqueId = "local:replacement"; // Display recovery may fail; stopping is still possible.
        hardware.commands.clear();
        session.stopTargetBeforeDebugOpen();
        assertEquals(Arrays.asList("stop:" + PKG), hardware.commands);
        assertFalse(hardware.enabled);
        assertNotNull(store.pending);
    }

    @Test public void stopFailurePreventsTheDebugOpeningCallerFromProceeding() throws Exception {
        session.prepare(PKG);
        hardware.failStop = true;
        assertThrows(Exception.class, session::stopTargetBeforeDebugOpen);
        assertNotNull(store.pending);
    }

    @Test public void staleOwnershipAfterRebootDoesNotStopTheTargetOnDebugOpen() throws Exception {
        session.prepare(PKG);
        hardware.boot = "boot-two";
        hardware.commands.clear();
        session.stopTargetBeforeDebugOpen();
        assertTrue(hardware.commands.isEmpty());
        assertNull(store.pending);
    }

    private static String display(int id, String uniqueId, boolean enabled, String type, boolean presentation, String state) {
        return "  Display " + id + ":\n    mDisplayId=" + id
                + "\n    mPrimaryDisplayDevice=内置屏幕(" + uniqueId + ")\n    mIsEnabled=" + enabled
                + "\n    mBaseDisplayInfo=DisplayInfo{\"内置屏幕\", displayId " + id + ", "
                + (presentation ? "FLAG_PRESENTATION, " : "") + "FLAG_SECURE, real 976 x 596, state "
                + state + ", committedState " + state + ", type " + type + ", uniqueId \"" + uniqueId + "\"}\n";
    }

    private static final class FakeStore implements RearDisplaySession.Store {
        RearDisplaySession.Pending pending;
        boolean failSave;
        public RearDisplaySession.Pending load() { return pending; }
        public void save(RearDisplaySession.Pending value) throws Exception {
            if (failSave) throw new IOException("disk full");
            pending = value;
        }
        public void clear() { pending = null; }
    }

    private static final class FakeHardware implements RearDisplaySession.Hardware {
        int rearId = 1;
        String uniqueId = "local:rear", boot = "boot-one", state = "OFF", extra = "", overrideDump;
        boolean enabled = true, failEnable, failWake, silentWakeFailure, failAfterDisable, failStop;
        Runnable beforeDisable;
        final List<String> commands = new ArrayList<>();
        public String displayDump() {
            return overrideDump != null ? overrideDump : display(0, "local:main", true, "INTERNAL", false, "ON")
                    + display(rearId, uniqueId, enabled, "INTERNAL", true, state) + extra;
        }
        public String bootId() { return boot; }
        public void disable(int id) throws Exception {
            if (beforeDisable != null) beforeDisable.run();
            commands.add("disable:" + id); enabled = false; state = "OFF";
            if (failAfterDisable) throw new IOException("interrupted after disable");
        }
        public void enable(int id) throws Exception {
            commands.add("enable:" + id);
            if (failEnable) throw new IOException("permission lost");
            enabled = true;
        }
        public void wake(int id) throws Exception {
            commands.add("wake:" + id);
            if (failWake) throw new IOException("wakeup failed");
            if (!silentWakeFailure) state = "ON";
        }
        public void stopApp(String packageName) throws Exception {
            commands.add("stop:" + packageName);
            if (failStop) throw new IOException("force-stop failed");
        }
    }
}
