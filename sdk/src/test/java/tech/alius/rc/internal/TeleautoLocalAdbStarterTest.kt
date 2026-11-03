package tech.alius.rc.internal

import org.junit.Assert.assertEquals
import org.junit.Test

class TeleautoLocalAdbStarterTest {
    @Test fun returnsAvailableWithoutRunningCommands() {
        var commands = 0
        val starter = TeleautoLocalAdbStarter(
            isListening = { true },
            runCommand = { commands++; TeleautoLocalAdbCommandResult(0, "") },
            pause = {},
        )

        assertEquals(TeleautoLocalAdbStatus.AVAILABLE, starter.ensureAvailable())
        assertEquals(0, commands)
    }

    @Test fun startsAndVerifiesPort() {
        var probes = 0
        val starter = TeleautoLocalAdbStarter(
            isListening = { ++probes >= 3 },
            runCommand = { TeleautoLocalAdbCommandResult(0, "") },
            pause = {},
        )

        assertEquals(TeleautoLocalAdbStatus.STARTED, starter.ensureAvailable())
    }

    @Test fun reportsPropertyPermissionFailure() {
        val starter = TeleautoLocalAdbStarter(
            isListening = { false },
            runCommand = { TeleautoLocalAdbCommandResult(1, "Failed to set property: Permission denied") },
            pause = {},
        )

        assertEquals(TeleautoLocalAdbStatus.PERMISSION_DENIED, starter.ensureAvailable())
    }

    @Test fun reportsTimeoutWhenCommandsSucceedButPortStaysClosed() {
        val starter = TeleautoLocalAdbStarter(
            isListening = { false },
            runCommand = { TeleautoLocalAdbCommandResult(0, "") },
            pause = {},
        )

        assertEquals(TeleautoLocalAdbStatus.START_TIMEOUT, starter.ensureAvailable())
    }
}
