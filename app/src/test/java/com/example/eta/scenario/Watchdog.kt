package com.example.eta.scenario

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * Runs [block] on this thread under a hard real-time [limit].
 *
 * A timer thread waits for the limit. When the limit passes while [block] is still
 * running, it records a dump of every thread and interrupts the calling thread.
 * A thread parked in a coroutine or a `Future` wait throws
 * `InterruptedException` out of `runBlocking`, so the test fails at once with the
 * dump instead of hanging. The failure names the stuck code: the dump lists the
 * caller's stack and the stacks of the Main dispatcher, Room and the Robolectric
 * looper.
 *
 * A thread that is blocked in something uninterruptible cannot be stopped from
 * here; the Gradle test timeout (`app/build.gradle.kts`) is the backstop for that.
 */
internal fun <T> withDeadline(limit: Duration, block: () -> T): T {
    val caller = Thread.currentThread()
    val lock = Any()
    var finished = false
    var dump: String? = null
    val timer = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "scenario-watchdog").apply { isDaemon = true }
    }
    timer.schedule(
        {
            synchronized(lock) {
                if (!finished) {
                    dump = threadDump()
                    caller.interrupt()
                }
            }
        },
        limit.inWholeMilliseconds,
        TimeUnit.MILLISECONDS,
    )
    try {
        val result = block()
        synchronized(lock) {
            dump?.let { throw overrun(limit, it, cause = null) }
        }
        return result
    } catch (failure: Throwable) {
        val captured = synchronized(lock) { dump }
        if (captured != null) throw overrun(limit, captured, failure)
        throw failure
    } finally {
        synchronized(lock) { finished = true }
        timer.shutdownNow()
        // The interrupt, if it came, is for this scenario only: clear it so the
        // next test on this thread starts clean.
        Thread.interrupted()
    }
}

private fun overrun(limit: Duration, dump: String, cause: Throwable?): AssertionError = AssertionError(
    "Szenario lief länger als $limit (Echtzeit) und wurde abgebrochen. " +
        "Wartet der Test auf einen Zustand, der nie eintritt? Thread-Abzug:\n$dump",
    cause,
)

/** Every live thread with its state and full stack. */
private fun threadDump(): String = Thread.getAllStackTraces().entries
    .sortedBy { it.key.name }
    .joinToString(separator = "\n") { (thread, frames) ->
        val header = "\"${thread.name}\" ${thread.state}" + if (thread.isDaemon) " (daemon)" else ""
        val stack = frames.joinToString(separator = "") { "\n    at $it" }
        header + stack
    }
