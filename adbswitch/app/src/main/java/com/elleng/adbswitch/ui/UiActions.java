package com.elleng.adbswitch.ui;

import java.util.List;

/** Platform operations. UI components never write Android settings themselves. */
public interface UiActions {
    enum Toggle { MASTER, USB, WIRELESS, DEVELOPER, LINKAGE, KILL, NOTIFY }
    void toggle(Toggle toggle, boolean enabled);
    void selectAuth(String mode);
    void setThemeMode(ThemeMode mode);
    void retryAuth();
    void saveApps(List<String> packages);
    void setDefaultApp(String packageName);
    void setCloseRear(String packageName, boolean enabled);
    void launchApp(String packageName);
    void createShortcut(String packageName);
    void restoreRearDisplay();
    void testNotification();
    void openNotificationSettings();
    void openRepository();
}
