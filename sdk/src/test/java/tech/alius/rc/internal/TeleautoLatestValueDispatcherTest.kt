package tech.alius.rc.internal

import java.util.ArrayDeque
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Test

class TeleautoLatestValueDispatcherTest {
    @Test fun queuesOneTaskAndDeliversLatestValue() {
        val executor = ManualExecutor()
        val delivered = mutableListOf<Int>()
        val dispatcher = TeleautoLatestValueDispatcher({ executor }, delivered::add)
        repeat(1_000) { dispatcher.offer(it) }
        assertEquals(1, executor.size)
        executor.runNext()
        assertEquals(listOf(999), delivered)
        assertEquals(0, executor.size)
    }

    @Test fun valueOfferedDuringDeliveryGetsAFollowingTask() {
        val executor = ManualExecutor()
        lateinit var dispatcher: TeleautoLatestValueDispatcher<Int>
        val delivered = mutableListOf<Int>()
        dispatcher = TeleautoLatestValueDispatcher({ executor }) {
            delivered += it
            if (it == 1) dispatcher.offer(2)
        }
        dispatcher.offer(1)
        executor.runNext()
        assertEquals(1, executor.size)
        executor.runNext()
        assertEquals(listOf(1, 2), delivered)
    }

    @Test fun clearDropsQueuedValue() {
        val executor = ManualExecutor()
        val delivered = mutableListOf<Int>()
        val dispatcher = TeleautoLatestValueDispatcher({ executor }, delivered::add)
        dispatcher.offer(1)
        dispatcher.clear()
        executor.runNext()
        assertEquals(emptyList<Int>(), delivered)
    }

    private class ManualExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()
        val size get() = tasks.size
        override fun execute(command: Runnable) { tasks.add(command) }
        fun runNext() { tasks.removeFirst().run() }
    }
}
