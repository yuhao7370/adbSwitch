package com.elleng.adbswitch.ui

import org.junit.Assert.*
import org.junit.Test

class MainUiStateTest {
    @Test fun writeRequiresCurrentAuthorizationAndConfirmation() {
        val state = MainUiState()
        assertFalse(state.canWrite)
        state.authorized = true
        state.known = true // A previous launch's state is not permission to write.
        assertFalse(state.canWrite)
        state.confirmed = true
        assertTrue(state.canWrite)
        state.busy = true
        assertFalse(state.canWrite)
        state.busy = false
        state.authorized = false
        assertFalse(state.canWrite)
    }

    @Test fun partialDebugRemainsOnRegardlessOfDeveloperOptions() {
        val state = MainUiState()
        state.dev = true
        assertFalse(state.anyOn)
        state.usb = true
        assertTrue(state.anyOn)
        state.usb = false
        state.wireless = true
        assertTrue(state.anyOn)
    }
}
