package tech.alius.rc.model

/** Stable SDK result; the application owns localized messages and presentation. */
enum class TeleautoScreenInputResult { ACCEPTED, UNLOCK_REQUIRED, INVALID_INPUT, LICENSE_REQUIRED }

class TeleautoScreenInputGate(private val authorized: () -> Boolean = { true }) {
    @Synchronized fun invalidate() { generation++ }
    @Volatile var locked: Boolean = false
        private set
    @Volatile var generation: Long = 0L
        private set
    @Synchronized fun setLocked(value: Boolean) {
        if (locked == value) return
        generation++
        locked = value
    }
    @Synchronized fun runIfOpen(expected: Long = generation, action: () -> Unit): Boolean {
        if (!authorized() || locked || generation != expected) return false
        action()
        return true
    }
    fun check(): TeleautoScreenInputResult = if (!authorized()) TeleautoScreenInputResult.LICENSE_REQUIRED else if (locked) TeleautoScreenInputResult.UNLOCK_REQUIRED else TeleautoScreenInputResult.ACCEPTED
}

/** Shared by collectors and transports without initializing Android input readers. */
internal object TeleautoControlOutputGate { val gate = TeleautoScreenInputGate { tech.alius.rc.license.TeleautoLicenseRuntime.isUsable() } }
