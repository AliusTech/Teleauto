package tech.alius.rc.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Paths

class TeleautoRcInputProfilesJsonTest {
    @Test
    fun sharedProfilesContainRequiredModelsAndCommands() {
        val json = listOf(
            Paths.get("djirc/src/main/assets/dji_rc_profiles.json").toFile(),
            Paths.get("src/main/assets/dji_rc_profiles.json").toFile(),
        ).first(File::isFile).readText()

        assertTrue(json.contains("\"id\": \"rm500\""))
        assertTrue(json.contains("\"id\": \"rm700\""))
        assertTrue(json.contains("\"event_command\": \"exec-out:cat /dev/input/event1\""))
        assertTrue(json.contains("\"logcat_command\": \"shell:logcat -T 1 -s KeyCodeListener:D *:S\""))
        assertTrue(json.contains("\"ACTION_KEY_C3\": \"C3\""))
        assertTrue(!json.contains("\"id\": \"rm510\""))
        assertTrue(!json.contains("\"id\": \"rc520\""))
        // ag501 is now an alias of the rm500 hardware profile, not a third
        // standalone profile entry.
        assertEquals(2, Regex("\"id\"\\s*:").findAll(json).count())
    }
}
