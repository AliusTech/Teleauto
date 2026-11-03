package tech.alius.rc.internal

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** Keeps at most one executor task queued while always delivering the newest value. */
internal class TeleautoLatestValueDispatcher<T>(
    private val executor: () -> Executor,
    private val deliver: (T) -> Unit,
) {
    private val scheduled = AtomicBoolean(false)
    private val lock = Any()
    private var latest: T? = null
    private var generation = 0L

    fun offer(value: T) {
        synchronized(lock) { latest = value }
        schedule()
    }

    fun clear() {
        synchronized(lock) {
            latest = null
            generation++
        }
    }

    private fun schedule() {
        if (!scheduled.compareAndSet(false, true)) return
        val taskGeneration = synchronized(lock) { generation }
        try {
            executor().execute {
                val value = synchronized(lock) {
                    if (taskGeneration == generation) latest.also { latest = null } else null
                }
                if (value != null) deliver(value)
                scheduled.set(false)
                if (synchronized(lock) { latest != null } ) schedule()
            }
        } catch (_: Exception) {
            scheduled.set(false)
        }
    }
}
