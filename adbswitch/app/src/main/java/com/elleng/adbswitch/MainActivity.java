package com.elleng.adbswitch;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.core.content.ContextCompat;
import com.elleng.adbswitch.ui.MainUiState;
import com.elleng.adbswitch.ui.MiuixHost;
import com.elleng.adbswitch.ui.QuickApp;
import com.elleng.adbswitch.ui.ThemeMode;
import com.elleng.adbswitch.ui.UiActions;
import com.elleng.adbswitch.util.RootUtil;
import java.util.ArrayList;
import java.util.List;
import rikka.shizuku.Shizuku;

/** Android/controller adapter. All application UI is composed by the miuix host. */
public class MainActivity extends ComponentActivity implements UiActions {
    public static final String EXTRA_SETTINGS = "extra_settings";
    private static final String ACTION_OPEN_HOME = "com.elleng.adbswitch.action.OPEN_HOME";
    private static final String ACTION_DESKTOP_LINKAGE = "com.elleng.adbswitch.action.DESKTOP_LINKAGE";
    private static final String EXTRA_LINKAGE_PKG = "extra_linkage_pkg";
    private static final String SC_ID_LAUNCH_APP = "launch_linkage_app";
    private static final String TAG = "AdbSwitch";
    private static final String PREFS = "cfg";
    private static final String PREF_LINKAGE = "linkage_enabled";
    private static final String PREF_NOTIFY = "notify_on_close";
    private static final String PREF_KILL_BEFORE_OPEN = "kill_before_open";
    private static final String PREF_THEME_MODE = "theme_mode";
    private static final String LIVE_UPDATE_CHANNEL_ID = "live_update_test";
    private static final String[] OBSERVED_KEYS = {DebugController.KEY_USB,
            DebugController.KEY_WIRELESS, DebugController.KEY_DEV};
    private static boolean liveChannelEnsured;
    private static boolean sLinkageLaunched, sAutoJumped, sNoDefaultToasted;
    private static long lastReadingToastAt;

    private MiuixHost ui;
    private boolean prefsLoaded, privilegedAvailable, busy, settingsObserverRegistered;
    private boolean authCheckedThisSession, checkingAuth;
    private String authNotice = "";
    private int authGeneration;
    private long lastShizukuRequestAt;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Shizuku.OnBinderReceivedListener shizukuBinderListener;
    private Shizuku.OnRequestPermissionResultListener shizukuPermListener;
    private final DebugController.StateListener stateListener = this::refresh;
    private final SharedPreferences.OnSharedPreferenceChangeListener configListener = (prefs, key) -> refresh(null);
    private final Runnable resyncRunnable = () -> {
        if (prefsLoaded) DebugController.invalidateAndRefresh(this);
    };
    private final android.database.ContentObserver settingsObserver =
            new android.database.ContentObserver(mainHandler) {
                @Override public void onChange(boolean selfChange) {
                    mainHandler.removeCallbacks(resyncRunnable);
                    mainHandler.postDelayed(resyncRunnable, 300);
                }
            };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new MiuixHost(this, this, readThemeMode());
        LinkagePrefs.migrate(this);
        DebugController.addStateListener(stateListener);
        getSharedPreferences(PREFS, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(configListener);
        initShizuku();
        if (!handleEntry(getIntent())) {
            showSettings();
            // A recreated screen restores its selected tab; don't replay its original entry.
            if (savedInstanceState == null) routeHomePage(getIntent());
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!busy && !handleEntry(intent)) {
            showSettings();
        }
        // Page navigation is safe while a controller operation is still running.
        routeHomePage(intent);
    }

    private void routeHomePage(Intent intent) {
        if (intent.getBooleanExtra(EXTRA_SETTINGS, false)) ui.showSettingsPage();
        else if (ACTION_OPEN_HOME.equals(intent.getAction())) ui.showAppsPage();
    }

    private void showSettings() {
        if (!prefsLoaded && Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        prefsLoaded = true;
        rebuildLinkageList();
        performAuthCheck();
        refresh(null);
        if (getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            observeSettings(true);
        }
    }

    private void refresh(DebugController.State current) {
        if (ui == null || isDestroyed() || isFinishing()) return;
        DebugController.State value = current == null ? DebugController.read(this) : current;
        if (!DebugController.isCalibrated()) value = DebugController.lastKnownState(this);
        MainUiState snapshot = new MainUiState();
        snapshot.usb = value.usb;
        snapshot.wireless = value.wireless;
        snapshot.dev = value.dev;
        snapshot.known = DebugController.isCalibrated() || DebugController.hasConfirmedBefore(this);
        snapshot.confirmed = DebugController.confirmedThisSession();
        snapshot.authorized = privilegedAvailable;
        snapshot.checkingAuth = checkingAuth;
        snapshot.authMode = AuthManager.getMode(this) == AuthManager.Mode.SHIZUKU ? "Shizuku" : "root";
        snapshot.authNotice = authNotice;
        snapshot.busy = busy;
        snapshot.rearPending = RearDisplayController.hasPending(this);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        snapshot.linkage = prefs.getBoolean(PREF_LINKAGE, false);
        snapshot.killBeforeOpen = prefs.getBoolean(PREF_KILL_BEFORE_OPEN, true);
        snapshot.notify = prefs.getBoolean(PREF_NOTIFY, true);
        snapshot.themeMode = readThemeMode();
        snapshot.liveUpdatesSupported = Build.VERSION.SDK_INT >= 36;
        snapshot.defaultPackage = LinkagePrefs.getDefault(this);
        List<QuickApp> apps = new ArrayList<>();
        for (String pkg : LinkagePrefs.getApps(this)) {
            apps.add(new QuickApp(pkg, getApplicationLabel(pkg).toString(), LinkagePrefs.closesRearDisplay(this, pkg)));
        }
        snapshot.apps = apps;
        snapshot.packageName = getPackageName();
        snapshot.buildTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date(BuildConfig.BUILD_TIME_MS));
        ui.update(snapshot);
        ToggleShortcuts.markDirty(this);
    }

    private void performAuthCheck() {
        if (!prefsLoaded || authCheckedThisSession || checkingAuth || isDestroyed()) return;
        checkingAuth = true;
        final int generation = ++authGeneration;
        final AuthManager.Mode mode = AuthManager.getMode(this);
        refresh(null);
        new Thread(() -> {
            final boolean ok = mode == AuthManager.Mode.SHIZUKU ? ShizukuUtil.available() : RootUtil.hasRoot();
            final boolean running = ShizukuUtil.running();
            mainHandler.post(() -> {
                // An old probe must not mark a newly selected authorization mode as available.
                if (isDestroyed() || isFinishing() || generation != authGeneration || mode != AuthManager.getMode(this)) return;
                checkingAuth = false;
                privilegedAvailable = ok;
                authCheckedThisSession = ok;
                authNotice = ok ? "" : getString(mode == AuthManager.Mode.ROOT ? R.string.auth_toast_root_no
                        : running ? R.string.auth_toast_shizuku_no : R.string.auth_need_start);
                if (ok) DebugController.invalidateAndRefresh(this);
                else if (mode == AuthManager.Mode.SHIZUKU) maybeRequestShizukuPermission(false);
                refresh(null);
            });
        }, "auth-probe").start();
    }

    @Override public void selectAuth(String value) {
        if (busy) return;
        AuthManager.Mode mode = "Shizuku".equals(value) ? AuthManager.Mode.SHIZUKU : AuthManager.Mode.ROOT;
        authGeneration++;
        checkingAuth = false;
        privilegedAvailable = false;
        authCheckedThisSession = false;
        authNotice = "";
        AuthManager.setMode(this, mode);
        DebugController.invalidateAndRefresh(this);
        maybeRequestShizukuPermission(true);
        performAuthCheck();
    }

    @Override public void retryAuth() {
        if (checkingAuth || busy) return;
        authCheckedThisSession = false;
        maybeRequestShizukuPermission(true);
        performAuthCheck();
    }

    @Override public void toggle(Toggle which, boolean enabled) {
        if (busy) return;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        switch (which) {
            case LINKAGE:
                if (enabled && LinkagePrefs.getDefault(this).isEmpty()) {
                    Toast.makeText(this, R.string.linkage_no_default, Toast.LENGTH_LONG).show();
                    return;
                }
                prefs.edit().putBoolean(PREF_LINKAGE, enabled).apply();
                ToggleShortcuts.markDirty(this);
                break;
            case KILL:
                prefs.edit().putBoolean(PREF_KILL_BEFORE_OPEN, enabled).apply();
                break;
            case NOTIFY:
                prefs.edit().putBoolean(PREF_NOTIFY, enabled).apply();
                if (DebugController.read(this).allOff()) {
                    if (enabled) AdbToggleService.start(this); else AdbToggleService.stop(this);
                }
                break;
            default:
                if (!privilegedAvailable) {
                    Toast.makeText(this, R.string.auth_need_grant, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!DebugController.confirmedThisSession()) { toastReading(this); return; }
                busy = true;
                refresh(null);
                DebugController.ResultCallback callback = (ok, state) -> onDebugWriteCompleted(which, enabled, ok, state);
                if (which == Toggle.MASTER) DebugController.setMaster(this, enabled, callback);
                else if (which == Toggle.USB) DebugController.setUsb(this, enabled, callback);
                else if (which == Toggle.WIRELESS) DebugController.setWireless(this, enabled, callback);
                else DebugController.setDev(this, enabled, callback);
                return;
        }
        refresh(null);
    }

    void onDebugWriteCompleted(Toggle which, boolean enabled, boolean ok, DebugController.State state) {
        busy = false;
        Context app = getApplicationContext();
        // The command belongs to the application, even if its initiating screen was recreated.
        if (ok && which == Toggle.MASTER) {
            if (state != null && state.anyOn()) AdbToggleService.stop(app);
            else AdbToggleService.syncCloseNotification(app);
        }
        ToggleShortcuts.markDirty(app);
        if (isDestroyed()) return;
        if (!ok) {
            privilegedAvailable = false;
            authCheckedThisSession = false;
            authNotice = getString(R.string.toast_root_fail);
            Toast.makeText(this, R.string.toast_root_fail, Toast.LENGTH_SHORT).show();
        } else {
            boolean actual = state != null && (which == Toggle.MASTER ? state.anyOn()
                    : which == Toggle.USB ? state.usb : which == Toggle.WIRELESS ? state.wireless : state.dev);
            if (actual != enabled) Toast.makeText(this, R.string.toast_write_not_effective, Toast.LENGTH_SHORT).show();
            else if (which == Toggle.DEVELOPER && !enabled) Toast.makeText(this, R.string.toast_devopts_hidden, Toast.LENGTH_LONG).show();
        }
        refresh(state);
    }

    private ThemeMode readThemeMode() {
        return ThemeMode.fromPreference(getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_THEME_MODE, null));
    }

    @Override public void setThemeMode(ThemeMode mode) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_THEME_MODE, mode.getPreferenceValue()).apply();
        refresh(null);
    }

    @Override public void saveApps(List<String> packages) {
        if (busy) return;
        LinkagePrefs.setApps(this, packages);
        rebuildLinkageList();
        ToggleShortcuts.markDirty(this);
    }
    @Override public void setDefaultApp(String pkg) {
        if (busy || !LinkagePrefs.getApps(this).contains(pkg)) return;
        LinkagePrefs.setDefault(this, pkg);
        refresh(null);
        ToggleShortcuts.markDirty(this);
    }
    @Override public void setCloseRear(String pkg, boolean enabled) {
        if (busy || !LinkagePrefs.getApps(this).contains(pkg)) return;
        LinkagePrefs.setClosesRearDisplay(this, pkg, enabled);
        refresh(null);
    }
    @Override public void launchApp(String pkg) {
        if (busy) return;
        if (!privilegedAvailable) {
            Toast.makeText(this, R.string.auth_need_grant, Toast.LENGTH_SHORT).show();
            return;
        }
        runLinkage(pkg, false);
    }
    @Override public void createShortcut(String pkg) { createDesktopShortcutFor(pkg); }
    @Override public void restoreRearDisplay() {
        if (busy || !privilegedAvailable) return;
        busy = true;
        refresh(null);
        RearDisplayController.restore(this, (ok, error) -> {
            busy = false;
            if (isDestroyed()) return;
            Toast.makeText(this, ok ? getString(R.string.rear_restored)
                    : getString(R.string.rear_restore_failed, error), Toast.LENGTH_LONG).show();
            refresh(null);
        });
    }

    private void rebuildLinkageList() {
        List<String> apps = LinkagePrefs.getApps(this);
        List<String> alive = new ArrayList<>();
        for (String pkg : apps) if (!isAppUninstalled(this, pkg)) alive.add(pkg);
        if (alive.size() != apps.size()) {
            LinkagePrefs.setApps(this, alive);
            ToggleShortcuts.markDirty(this);
            Toast.makeText(this, R.string.linkage_app_uninstalled, Toast.LENGTH_LONG).show();
        }
        if (alive.size() == 1 && LinkagePrefs.getDefault(this).isEmpty()) {
            LinkagePrefs.setDefault(this, alive.get(0));
            ToggleShortcuts.markDirty(this);
        }
        refresh(null);
    }

    @Override public void openRepository() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yuhao7370/adbSwitch"))); }
        catch (Exception e) { Toast.makeText(this, R.string.about_open_failed, Toast.LENGTH_SHORT).show(); }
    }
    @Override public void testNotification() {
        if (Build.VERSION.SDK_INT < 36) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        boolean promoted = false;
        try { promoted = (Boolean) NotificationManager.class.getMethod("canPostPromotedNotifications").invoke(manager); }
        catch (Exception ignored) { }
        sendLiveUpdateTest();
        if (promoted) Toast.makeText(this, R.string.live_update_test_sent, Toast.LENGTH_LONG).show();
        else ui.showNotificationPrompt();
    }
    @Override public void openNotificationSettings() {
        try { startActivity(new Intent("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS")
                .setData(Uri.parse("package:" + getPackageName()))); }
        catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())); }
            catch (Exception ignored) { }
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (!prefsLoaded) return;
        if (!privilegedAvailable) performAuthCheck();
        rebuildLinkageList();
        observeSettings(true);
    }
    @Override protected void onPause() {
        observeSettings(false);
        super.onPause();
    }
    @Override protected void onDestroy() {
        authGeneration++;
        mainHandler.removeCallbacksAndMessages(null);
        observeSettings(false);
        DebugController.removeStateListener(stateListener);
        getSharedPreferences(PREFS, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(configListener);
        try {
            if (shizukuBinderListener != null) Shizuku.removeBinderReceivedListener(shizukuBinderListener);
            if (shizukuPermListener != null) Shizuku.removeRequestPermissionResultListener(shizukuPermListener);
        } catch (Throwable ignored) { }
        super.onDestroy();
    }
    private void initShizuku() {
        try {
            shizukuBinderListener = new Shizuku.OnBinderReceivedListener() {
                @Override
                public void onBinderReceived() {
                    android.util.Log.i(TAG, "Shizuku binder received");
                    // 服务就绪：Shizuku 模式且未授权时主动弹授权（force=false 走防抖）
                    maybeRequestShizukuPermission(false);
                    // 无论是否已授权都重测一次，刷新授权状态行与控件可用性
                    onAuthorizationChanged();
                }
            };
            Shizuku.addBinderReceivedListenerSticky(shizukuBinderListener);
            shizukuPermListener = new Shizuku.OnRequestPermissionResultListener() {
                @Override
                public void onRequestPermissionResult(int requestCode, int grantResult) {
                    if (requestCode == AuthManager.SHIZUKU_PERM_REQUEST) {
                        android.util.Log.i(TAG, "Shizuku permission result=" + grantResult);
                        onAuthorizationChanged();
                    }
                }
            };
            Shizuku.addRequestPermissionResultListener(shizukuPermListener);
        } catch (Throwable t) {
            android.util.Log.w(TAG, "initShizuku failed", t);
        }
    }

    private void onAuthorizationChanged() {
        if (AuthManager.getMode(this) != AuthManager.Mode.SHIZUKU) return;
        // Invalidate a probe that may already have read the pre-grant state.
        ++authGeneration;
        checkingAuth = false;
        authCheckedThisSession = false;
        privilegedAvailable = false;
        performAuthCheck();
    }

    private boolean handleEntry(Intent in) {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);

        if (RearDisplayController.recoverOnFirstOpen(this)) {
            sLinkageLaunched = true;
            RearDisplayController.restore(this, (ok, error) -> {
                Toast.makeText(getApplicationContext(), ok ? getString(R.string.rear_restored)
                        : getString(R.string.rear_restore_failed, error), Toast.LENGTH_LONG).show();
            });
            return false; // Recovery takes priority over automatic/shortcut launches.
        }

        // 动态 shortcut「启动快捷应用」：直接跳目标应用（不触发关调试/常驻通知等动作），
        // 故不依赖快捷打开开关，开没开都能用。
        if (SC_ID_LAUNCH_APP.equals(in.getAction())) {
            String pkg = LinkagePrefs.getDefault(this);
            if (pkg.isEmpty()) {
                Toast.makeText(this, R.string.linkage_no_default, Toast.LENGTH_LONG).show();
                finish();
                return true;
            }
            if (!launchTargetDirect(pkg)) {
                // 默认目标已失效：提示并落到设置页（已卸载的顺手清掉），不再静默闪回桌面
                handleLinkageTargetGone(pkg);
                return false;
            }
            finish();
            return true;
        }

        // 打开主页的两种入口：通知栏「应用设置」传来的 EXTRA_SETTINGS(boolean)，
        // 或「打开主页」shortcut 携带的自定义 action。
        // 注意不能用 <extra> 传标记：shortcuts 解析会把 "1" 存成 Integer，
        // getStringExtra() 内部吞掉 ClassCastException 返回 null，判定永远失效。
        boolean openSettings = ACTION_OPEN_HOME.equals(in.getAction())
                || in.getBooleanExtra(EXTRA_SETTINGS, false);

        // 桌面快捷方式「创建桌面快捷方式」：先打开本 App，关掉调试后联动启动目标应用。
        // 这是显式触发（用户点了快捷方式），故不走 PREF_LINKAGE 开关、也不受
        // sLinkageLaunched 守卫限制——每次点都应重新关调试并跳目标应用。
        if (ACTION_DESKTOP_LINKAGE.equals(in.getAction())) {
            String pkg = in.getStringExtra(EXTRA_LINKAGE_PKG);
            // 桌面快捷方式始终带包名（见 createDesktopShortcutFor），缺包名的场景已废弃；
            // 真缺了就当普通入口落到设置页，不再单独提示。
            if (pkg == null || pkg.isEmpty()) {
                return false;
            }
            // Android 13+ 缺通知权限时，常驻通知会被系统抑制，联动流程依赖它，
            // 故先提示并落到设置页（showSettings 会补发权限申请），不跳转。
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.linkage_need_notif, Toast.LENGTH_LONG).show();
                return false;
            }
            runLinkage(pkg, false);
            return true;
        }

        boolean linkage = sp.getBoolean(PREF_LINKAGE, false);

        // —— 快捷打开：从启动器打开时，自动关闭调试并打开「默认快捷应用」 ——
        if (linkage && !openSettings) {
            String pkg = LinkagePrefs.getDefault(this);
            if (!pkg.isEmpty()) {
                // Android 13+ 未授予通知权限时，前台服务通知会被系统抑制；
                // 联动整条流程依赖这条常驻通知，故缺权限时先申请、不跳转，
                // 留在设置页（showSettings 里会补发申请）。
                if (Build.VERSION.SDK_INT >= 33
                        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.linkage_need_notif,
                            Toast.LENGTH_LONG).show();
                    return false;
                }
                // 已经跳过一次：不再自动跳。
                // 否则从联动应用返回本 App（或再次点图标）时会被反复弹回去，
                // 用的人根本来不及用设置页。需要再跳时用动态快捷方式手动触发。
                // 只在上一次是「启动自动跳转」时提示，且只提示一次（手动跳转回来不必说
                // 「启动时已自动跳转」，反复打开也不该反复弹）。
                if (sLinkageLaunched) {
                    if (sAutoJumped) {
                        sAutoJumped = false;
                        Toast.makeText(this, R.string.linkage_skipped, Toast.LENGTH_LONG).show();
                    }
                    return false;
                }
                runLinkage(pkg, true);
                return true;
            }
            // 开了联动却没设默认跳转应用：提示并落到普通界面。
            // 每个进程只提示一次：配置没修好前每次打开都会走到这里，反复弹纯属打扰。
            if (!sNoDefaultToasted) {
                sNoDefaultToasted = true;
                Toast.makeText(this, R.string.linkage_no_default, Toast.LENGTH_LONG).show();
            }
        }
        return false;
    }

    private void runLinkage(String pkg, boolean autoJump) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) {
            handleLinkageTargetGone(pkg);
            if (!prefsLoaded) showSettings();
            return;
        }
        sLinkageLaunched = true; // 任意来源跳转都记一笔：回来时不再自动跳，避免反复弹回
        if (autoJump) {
            sAutoJumped = true; // 区分来源：回设置页时只有自动跳转才提示「启动时已自动跳转」
        }
        busy = true;
        refresh(null);
        DebugController.quickLaunch(this, pkg, launch, (ok, s, error) -> {
            busy = false;
            if (isDestroyed()) return;
            if (!ok) {
                Toast.makeText(this, getString(R.string.quick_launch_failed, error), Toast.LENGTH_LONG).show();
                if (!prefsLoaded) showSettings();
                refresh(s);
                return;
            }
            AdbToggleService.syncCloseNotification(this);
            if (autoJump) {
                Toast.makeText(this, R.string.linkage_auto_jumped, Toast.LENGTH_LONG).show();
            }
            finish();
        });
    }

    private void handleLinkageTargetGone(String pkg) {
        if (isAppUninstalled(this, pkg)) {
            List<String> apps = new ArrayList<>(LinkagePrefs.getApps(this));
            if (apps.remove(pkg)) {
                LinkagePrefs.setApps(this, apps);
                ToggleShortcuts.markDirty(this);
            }
            Toast.makeText(this, R.string.linkage_app_uninstalled, Toast.LENGTH_LONG).show();
            if (prefsLoaded) {
                rebuildLinkageList();
            }
        } else {
            Toast.makeText(this, R.string.linkage_app_unlaunchable, Toast.LENGTH_LONG).show();
        }
    }

    private static boolean isAppUninstalled(Context ctx, String pkg) {
        try {
            ctx.getPackageManager().getApplicationInfo(pkg, 0);
            return false;
        } catch (Throwable t) {
            return true;
        }
    }

    private CharSequence getApplicationLabel(String pkg) {
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(pkg, 0));
        } catch (Throwable t) {
            return pkg;
        }
    }

    private boolean launchTargetDirect(String pkg) {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) return false;
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception error) {
            android.util.Log.w(TAG, "launchApp failed: " + pkg, error);
            return false;
        }
    }

    private void observeSettings(boolean register) {
        try {
            android.content.ContentResolver cr = getContentResolver();
            if (register) {
                if (settingsObserverRegistered) {
                    return;
                }
                for (String key : OBSERVED_KEYS) {
                    cr.registerContentObserver(
                            Settings.Global.getUriFor(key), false, settingsObserver);
                }
                settingsObserverRegistered = true;
            } else {
                if (!settingsObserverRegistered) {
                    return;
                }
                cr.unregisterContentObserver(settingsObserver);
                settingsObserverRegistered = false;
            }
        } catch (Throwable t) {
            android.util.Log.w(TAG, "observe settings failed", t);
        }
    }

    private void maybeRequestShizukuPermission(boolean force) {
        if (AuthManager.getMode(this) != AuthManager.Mode.SHIZUKU) {
            return;
        }
        if (!ShizukuUtil.running()) {
            android.util.Log.i(TAG, "Shizuku binder not alive, skip permission request");
            return;
        }
        if (ShizukuUtil.permissionGranted()) {
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        if (!force && now - lastShizukuRequestAt < 5000) {
            return;
        }
        lastShizukuRequestAt = now;
        android.util.Log.i(TAG, "requesting Shizuku permission (force=" + force + ")");
        try {
            Shizuku.requestPermission(AuthManager.SHIZUKU_PERM_REQUEST);
        } catch (Throwable t) {
            android.util.Log.w(TAG, "requestPermission failed", t);
        }
    }

    private Drawable loadAppDrawable(String pkg) {
        try {
            return getPackageManager().getApplicationIcon(pkg);
        } catch (Throwable t) {
            return ContextCompat.getDrawable(this, R.mipmap.ic_launcher);
        }
    }

    private void createDesktopShortcutFor(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return; // 行点击传入的包名恒非空（缺包名场景已废弃），此守卫只防编程错误
        }
        CharSequence label = getApplicationLabel(pkg);
        // 关键：快捷方式必须先打开「本 App」，由 App 关掉调试再联动启动目标应用，
        // 而不是直接拉起目标 app（那样会绕过关调试的联动逻辑，等于什么都不做）。
        // 用显式 Intent 指向 MainActivity + 自定义 action，并把包名带进去，
        // 图标仍用「目标应用 + 本 App 角标」以便一眼认出这是哪个联动应用的快捷方式。
        Intent launch = new Intent(this, MainActivity.class);
        launch.setAction(ACTION_DESKTOP_LINKAGE);
        launch.putExtra(EXTRA_LINKAGE_PKG, pkg);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Bitmap icon = badgedShortcutIcon(pkg);

        // Android 8+（minSdk=27 必满足）优先用 LauncherApps 钉选（现代 launcher 才支持），失败再回退老广播
        if (requestPinShortcut(label, launch, icon)) {
            // 真实结果由回调给（PinShortcutReceiver）：钉上才提示「已创建」，
            // 用户取消 / 被 ROM 吞掉则超时兜底提示「未创建」。这里不再发「已请求」这类无结果提示。
            return;
        }
        Intent intent = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launch);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, label != null ? label.toString() : pkg);
        if (icon != null) {
            intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon);
        }
        intent.putExtra("duplicate", false);
        try {
            sendBroadcast(intent);
            // 两条路径都拿不到「是否真的落到桌面」的真实结果，只能提示已发起
            Toast.makeText(this, R.string.create_shortcut_done, Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            // 旧广播路径基本不会抛异常；真抛了只记日志（创建失败与否无从得知）
            android.util.Log.w(TAG, "INSTALL_SHORTCUT broadcast failed", t);
        }
    }

    private boolean requestPinShortcut(CharSequence label, Intent launch, Bitmap icon) {
        ShortcutManager sm = (ShortcutManager) getSystemService(SHORTCUT_SERVICE);
        if (sm == null || !sm.isRequestPinShortcutSupported()) {
            return false;
        }
        ShortcutInfo.Builder b = new ShortcutInfo.Builder(this,
                "desktop_linkage_" + System.currentTimeMillis());
        b.setIntent(launch);
        b.setShortLabel(label != null ? label : getString(R.string.app_name));
        if (icon != null) {
            b.setIcon(Icon.createWithBitmap(icon));
        }
        try {
            // 结果回调：按官方契约，只有真正钉上时才会收到（PendingIntent 按官方样例用
            // FLAG_IMMUTABLE）；取消/被拒不回调，故 arm() 挂超时兜底提示。
            Intent cb = new Intent(this, PinShortcutReceiver.class);
            PendingIntent result = PendingIntent.getBroadcast(this, 0, cb,
                    PendingIntent.FLAG_IMMUTABLE);
            boolean accepted = sm.requestPinShortcut(b.build(), result.getIntentSender());
            if (accepted) {
                PinShortcutReceiver.arm(this);
            }
            return accepted;
        } catch (Throwable t) {
            return false;
        }
    }

    private Bitmap badgedShortcutIcon(String pkg) {
        int size = Math.max(1, (int) (48 * getResources().getDisplayMetrics().density));
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);

        // 底层：联动应用图标
        Drawable base;
        try {
            base = getPackageManager().getApplicationIcon(pkg);
        } catch (Throwable t) {
            base = ContextCompat.getDrawable(this, R.mipmap.ic_launcher);
        }
        base.setBounds(0, 0, size, size);
        base.draw(c);

        // 角标：本 App 图标（右下角，约 1/3 大小），白底圆提高辨识度
        int badge = (int) (size * 0.36f);
        int margin = (int) (size * 0.05f);
        int left = size - badge - margin;
        int top = size - badge - margin;
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(0xFFFFFFFF);
        c.drawCircle(left + badge / 2f, top + badge / 2f, badge / 2f, dot);

        Drawable mine = ContextCompat.getDrawable(this, R.drawable.ic_shortcut_badge);
        mine.setBounds(left, top, left + badge, top + badge);
        mine.draw(c);

        return out;
    }

    private void sendLiveUpdateTest() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        if (!liveChannelEnsured) {
            NotificationChannel ch = new NotificationChannel(
                    LIVE_UPDATE_CHANNEL_ID, getString(R.string.live_update_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
            liveChannelEnsured = true;
        }
        Notification.Builder b = new Notification.Builder(this, LIVE_UPDATE_CHANNEL_ID);
        b.setSmallIcon(R.drawable.ic_tile)
                .setOngoing(true)
                .setContentTitle(getString(R.string.live_update_test_title))
                .setContentText(getString(R.string.live_update_test_text))
                .setPriority(Notification.PRIORITY_LOW);
        try {
            Notification.Builder.class
                    .getMethod("setRequestPromotedOngoing", boolean.class)
                    .invoke(b, true);
            Notification.Builder.class
                    .getMethod("setShortCriticalText", String.class)
                    .invoke(b, "测试中");
        } catch (Throwable ignored) {
        }
        // 点测试通知回到本应用设置页
        Intent main = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_SETTINGS, true);
        PendingIntent pi = PendingIntent.getActivity(this, 9, main, PendingIntent.FLAG_IMMUTABLE);
        b.setContentIntent(pi);
        try {
            nm.notify(99, b.build());
        } catch (Throwable ignored) {
        }
        // 测试通知必须能自行消失：它是 ongoing（滑动去不掉），若不主动取消就会永久驻留
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                nm.cancel(99);
            } catch (Throwable ignored) {
            }
        }, 10_000);
    }

    static void toastReading(Context ctx) {
        if (ctx == null) {
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastReadingToastAt < 2000) {
            return;
        }
        lastReadingToastAt = now;
        Toast.makeText(ctx, R.string.master_checking, Toast.LENGTH_SHORT).show();
    }
}
