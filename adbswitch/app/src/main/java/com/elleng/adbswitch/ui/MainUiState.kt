package com.elleng.adbswitch.ui

/** A fresh snapshot is published after every controller/configuration change. */
class MainUiState {
    @JvmField var usb = false
    @JvmField var wireless = false
    @JvmField var dev = false
    @JvmField var known = false
    @JvmField var confirmed = false
    @JvmField var authorized = false
    @JvmField var checkingAuth = true
    @JvmField var authMode = "root"
    @JvmField var authNotice = ""
    @JvmField var busy = false
    @JvmField var rearPending = false
    @JvmField var linkage = false
    @JvmField var killBeforeOpen = true
    @JvmField var notify = true
    @JvmField var themeMode = ThemeMode.SYSTEM
    @JvmField var liveUpdatesSupported = false
    @JvmField var defaultPackage = ""
    @JvmField var apps: List<QuickApp> = emptyList()
    @JvmField var buildTime = ""
    @JvmField var packageName = ""
    val anyOn: Boolean get() = usb || wireless
    val canWrite: Boolean get() = authorized && confirmed && !busy
}

data class QuickApp(val packageName: String, val label: String, val closeRear: Boolean)
