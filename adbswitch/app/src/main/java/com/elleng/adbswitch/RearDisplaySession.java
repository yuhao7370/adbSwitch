package com.elleng.adbswitch;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Device-independent display ownership and recovery. */
final class RearDisplaySession {
    static final class Display {
        final int id;
        final String uniqueId;
        final boolean enabled;
        final boolean internal;
        final boolean presentation;
        final String state;

        Display(int id, String uniqueId, boolean enabled, boolean internal,
                boolean presentation, String state) {
            this.id = id;
            this.uniqueId = uniqueId;
            this.enabled = enabled;
            this.internal = internal;
            this.presentation = presentation;
            this.state = state;
        }
    }

    static final class Pending {
        final String uniqueId;
        final String packageName;
        final String bootId;

        Pending(String uniqueId, String packageName, String bootId) {
            this.uniqueId = uniqueId;
            this.packageName = packageName;
            this.bootId = bootId;
        }
    }

    interface Hardware {
        String displayDump() throws Exception;
        String bootId() throws Exception;
        void disable(int id) throws Exception;
        void enable(int id) throws Exception;
        void wake(int id) throws Exception;
        void stopApp(String packageName) throws Exception;
    }

    interface Store {
        Pending load();
        void save(Pending pending) throws Exception;
        void clear() throws Exception;
    }

    private final Hardware hardware;
    private final Store store;

    RearDisplaySession(Hardware hardware, Store store) {
        this.hardware = hardware;
        this.store = store;
    }

    static List<Display> parse(String dump) {
        List<Display> displays = new ArrayList<>();
        if (dump == null) return displays;
        Pattern header = Pattern.compile("Display (\\d+):");
        Pattern identity = Pattern.compile("uniqueId\\s+\"([^\"]+)\"");
        Pattern power = Pattern.compile(",\\s*state ([A-Z_]+)");
        int id = -1;
        Boolean enabled = null;
        for (String line : dump.split("\\r?\\n")) {
            String text = line.trim();
            Matcher start = header.matcher(text);
            if (start.matches()) {
                id = Integer.parseInt(start.group(1));
                enabled = null;
            } else if (id >= 0 && text.startsWith("mIsEnabled=")) {
                String value = text.substring("mIsEnabled=".length());
                enabled = "true".equals(value) ? Boolean.TRUE
                        : "false".equals(value) ? Boolean.FALSE : null;
            } else if (id >= 0 && enabled != null && text.startsWith("mBaseDisplayInfo=DisplayInfo{")) {
                Matcher unique = identity.matcher(text);
                Matcher state = power.matcher(text);
                if (unique.find() && state.find()) {
                    displays.add(new Display(id, unique.group(1), enabled,
                            text.contains("type INTERNAL"), text.contains("FLAG_PRESENTATION"), state.group(1)));
                }
                id = -1; // Do not accidentally parse a later override/power-controller section.
            }
        }
        return displays;
    }

    void prepare(String packageName) throws Exception {
        if (packageName == null || !packageName.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) {
            throw new IllegalArgumentException("快捷应用包名无效");
        }
        String boot = hardware.bootId();
        if (boot == null || boot.trim().isEmpty()) throw new IllegalStateException("无法读取开机标识");
        Pending previous = store.load();
        if (previous != null) {
            Display owned = find(parse(hardware.displayDump()), previous.uniqueId);
            if (previous.bootId.equals(boot) && previous.packageName.equals(packageName)
                    && owned != null && !owned.enabled) return;
            restore();
        }

        Display rear = null;
        for (Display display : parse(hardware.displayDump())) {
            if (!isRear(display)) continue;
            if (rear != null) throw new IllegalStateException("检测到多块内置副屏，无法确定背屏");
            rear = display;
        }
        if (rear == null) throw new IllegalStateException("没有检测到可控制的小米内置背屏");
        if (!rear.enabled) return; // Respect a screen disabled by somebody else.

        store.save(new Pending(rear.uniqueId, packageName, boot));
        try {
            hardware.disable(rear.id);
            Display after = find(parse(hardware.displayDump()), rear.uniqueId);
            if (after == null || after.enabled) throw new IllegalStateException("背屏未成功停用");
        } catch (Exception error) {
            try { restore(); } catch (Exception recovery) { error.addSuppressed(recovery); }
            throw error;
        }
    }

    void stopTargetBeforeDebugOpen() throws Exception {
        Pending pending = store.load();
        if (pending == null) return;
        String boot = hardware.bootId();
        if (boot == null || boot.trim().isEmpty()) throw new IllegalStateException("无法读取开机标识");
        if (!pending.bootId.equals(boot)) {
            store.clear();
            return;
        }
        // Independent of the current shortcut list and the optional bulk force-stop setting.
        // This also works when display recovery itself is temporarily unavailable.
        if (!pending.packageName.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) {
            throw new IllegalStateException("恢复记录中的应用包名无效");
        }
        hardware.stopApp(pending.packageName);
    }

    void restore() throws Exception {
        Pending pending = store.load();
        if (pending == null) return;
        List<Display> displays = parse(hardware.displayDump());
        String boot = hardware.bootId();
        if (boot == null || boot.trim().isEmpty()) throw new IllegalStateException("无法读取开机标识");
        if (!pending.bootId.equals(boot)) {
            // A reboot ends the old ownership. Never override post-reboot choices.
            store.clear();
            return;
        }
        Display rear = find(displays, pending.uniqueId);
        if (rear == null || !isRear(rear)) throw new IllegalStateException("原背屏未找到，已保留恢复记录");
        if (!pending.packageName.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) {
            throw new IllegalStateException("恢复记录中的应用包名无效");
        }
        hardware.stopApp(pending.packageName);
        if (!rear.enabled) hardware.enable(rear.id);
        // Enabling the logical display alone leaves the Xiaomi power group asleep.
        hardware.wake(rear.id);
        Display after = find(parse(hardware.displayDump()), pending.uniqueId);
        if (after == null || !after.enabled || !"ON".equals(after.state)) {
            throw new IllegalStateException("背屏尚未唤醒，已保留恢复记录，请重试");
        }
        store.clear();
    }

    private static boolean isRear(Display display) {
        return display.id > 0 && display.internal && display.presentation && display.uniqueId.startsWith("local:");
    }

    private static Display find(List<Display> displays, String uniqueId) {
        for (Display display : displays) if (display.uniqueId.equals(uniqueId)) return display;
        return null;
    }
}
