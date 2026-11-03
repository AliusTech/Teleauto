package tech.alius.rc.model

/** Only the simultaneous physical C1/C2 hold is evaluated while outputs are locked. */
internal class TeleautoPhysicalUnlockHold(private val durationMs: Long = 2000L) {
    private var startedAt: Long? = null
    val active get() = startedAt != null
    fun update(c1: Boolean, c2: Boolean, nowMs: Long) {
        if (!c1 || !c2) reset() else if (startedAt == null) startedAt = nowMs
    }
    fun progress(nowMs: Long): Float = startedAt?.let {
        ((nowMs - it).toFloat() / durationMs).coerceIn(0f, 1f)
    } ?: 0f
    fun reset() { startedAt = null }
}
