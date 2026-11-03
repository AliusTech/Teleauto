package tech.alius.rc

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.alius.rc.model.TeleautoInputConnectionIssue
import tech.alius.rc.model.TeleautoRuntimeEnvironmentState

class TeleautoRuntimeEnvironmentStatusTest {
    @Test fun ordinaryStartupDelayStaysChecking() {
        assertEquals(TeleautoRuntimeEnvironmentState.CHECKING, resolveInputEnvironmentStatus(false, null).state)
    }

    @Test fun connectedBackendIsReady() {
        assertEquals(TeleautoRuntimeEnvironmentState.READY, resolveInputEnvironmentStatus(true, null).state)
    }

    @Test fun onlyExplicitTerminalIssuesRequirePreparation() {
        listOf(
            TeleautoInputConnectionIssue.DIRECT_INPUT_PERMISSION_REQUIRED,
            TeleautoInputConnectionIssue.ADB_TRANSPORT_PREPARATION_REQUIRED,
            TeleautoInputConnectionIssue.ADB_AUTHORIZATION_REQUIRED,
        ).forEach { issue ->
            assertEquals(
                TeleautoRuntimeEnvironmentState.PREPARATION_REQUIRED,
                resolveInputEnvironmentStatus(false, issue).state,
            )
        }
    }

    @Test fun recoverySuppressesPreparation() {
        assertEquals(
            TeleautoRuntimeEnvironmentState.READY,
            resolveInputEnvironmentStatus(true, TeleautoInputConnectionIssue.ADB_TRANSPORT_PREPARATION_REQUIRED).state,
        )
    }
}
