package com.elleng.adbswitch;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.elleng.adbswitch.util.RootUtil;

import java.io.IOException;

/** Android shell/persistence adapter. All hardware work uses DebugController's IO queue. */
final class RearDisplayController {
    private static final String PREFS = "rear_display_recovery";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean checkedRecoveryThisProcess;

    interface Callback { void onComplete(boolean ok, String error); }

    private RearDisplayController() { }

    static boolean hasPending(Context context) {
        return prefs(context).contains("unique_id");
    }

    static synchronized boolean recoverOnFirstOpen(Context context) {
        if (checkedRecoveryThisProcess) return false;
        checkedRecoveryThisProcess = true;
        return hasPending(context);
    }

    static void prepareBlocking(Context context, String packageName) throws Exception {
        if (!LinkagePrefs.closesRearDisplay(context, packageName)) {
            restoreBlocking(context);
            return;
        }
        requireRecoveryNotification(context);
        String manufacturer = command(context, "getprop ro.product.manufacturer").trim();
        if (!"xiaomi".equalsIgnoreCase(manufacturer)) {
            throw new IOException("背屏控制目前仅支持带内置背屏的小米手机");
        }
        session(context).prepare(packageName);
    }

    static void restoreBlocking(Context context) throws Exception {
        if (hasPending(context)) session(context).restore();
    }

    static void stopTargetBeforeDebugOpen(Context context) throws Exception {
        if (hasPending(context)) session(context).stopTargetBeforeDebugOpen();
    }

    static void restore(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        DebugController.executeSerial(() -> {
            String error = null;
            try { restoreBlocking(app); } catch (Exception e) { error = message(e); }
            final String result = error;
            MAIN.post(() -> {
                notifyChanged(app);
                if (callback != null) callback.onComplete(result == null, result);
            });
        });
    }

    static void reportRecoveryFailure(Context context, Exception error) {
        MAIN.post(() -> Toast.makeText(context,
                context.getString(R.string.rear_restore_failed, message(error)), Toast.LENGTH_LONG).show());
    }

    static void notifyChanged(Context context) {
        if (hasPending(context) || DebugController.read(context).allOff()) {
            AdbToggleService.syncCloseNotification(context);
        } else {
            AdbToggleService.stop(context);
        }
        DebugController.publishState(context);
    }

    static String message(Exception error) {
        String value = error.getMessage();
        return value == null || value.isEmpty() ? error.getClass().getSimpleName() : value;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void requireRecoveryNotification(Context context) throws IOException {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            throw new IOException("请先允许通知，以便随时恢复背屏");
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || !manager.areNotificationsEnabled()) throw new IOException("请先开启本应用的通知");
        android.app.NotificationChannel channel = manager.getNotificationChannel(AdbToggleService.CHANNEL_ID);
        if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
            throw new IOException("请先开启调试开关的通知渠道");
        }
    }

    private static String command(Context context, String command) throws IOException {
        RootUtil.Result result = DebugController.runPrivileged(context, command);
        if (!result.ok()) {
            String detail = result.err == null ? "" : result.err.trim();
            if (detail.length() > 180) detail = detail.substring(0, 180);
            throw new IOException("系统命令执行失败（" + result.code + "）" + (detail.isEmpty() ? "" : "：" + detail));
        }
        return result.out;
    }

    private static RearDisplaySession session(Context context) {
        return new RearDisplaySession(new RearDisplaySession.Hardware() {
            public String displayDump() throws Exception { return command(context, "dumpsys display"); }
            public String bootId() throws Exception { return command(context, "cat /proc/sys/kernel/random/boot_id").trim(); }
            public void disable(int id) throws Exception {
                command(context, "cmd display disable-display " + id);
                awaitState(id, false, false);
            }
            public void enable(int id) throws Exception { command(context, "cmd display enable-display " + id); }
            public void wake(int id) throws Exception {
                command(context, "cmd power wakeup --display-id " + id);
                awaitState(id, true, true);
            }
            public void stopApp(String packageName) throws Exception { command(context, "am force-stop " + packageName); }
            private void awaitState(int id, boolean enabled, boolean awake) throws Exception {
                for (int attempt = 0; attempt < 10; attempt++) {
                    for (RearDisplaySession.Display display : RearDisplaySession.parse(displayDump())) {
                        if (display.id == id && display.enabled == enabled && (!awake || "ON".equals(display.state))) return;
                    }
                    Thread.sleep(150);
                }
                throw new IOException(awake ? "背屏尚未唤醒" : "背屏停用未生效");
            }
        }, new RearDisplaySession.Store() {
            public RearDisplaySession.Pending load() {
                SharedPreferences prefs = prefs(context);
                String unique = prefs.getString("unique_id", null);
                return unique == null ? null : new RearDisplaySession.Pending(unique,
                        prefs.getString("package_name", ""), prefs.getString("boot_id", ""));
            }
            public void save(RearDisplaySession.Pending pending) throws Exception {
                if (!prefs(context).edit().putString("unique_id", pending.uniqueId)
                        .putString("package_name", pending.packageName).putString("boot_id", pending.bootId).commit()) {
                    throw new IOException("无法保存背屏恢复记录，未执行关闭");
                }
                AdbToggleService.start(context);
            }
            public void clear() throws Exception {
                if (!prefs(context).edit().clear().commit()) throw new IOException("无法清理恢复记录，请重试");
            }
        });
    }
}
