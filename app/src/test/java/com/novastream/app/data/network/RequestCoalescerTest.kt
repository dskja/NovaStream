package com.novastream.app.data.network

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class RequestCoalescerTest {

    @Test
    fun coalesce_executesBlockOnceForSameKey() = runTest {
        val coalescer = RequestCoalescer()
        val counter = AtomicInteger(0)

        coroutineScope {
            val jobs = List(5) {
                async {
                    coalescer.coalesce("home") {
                        counter.incrementAndGet()
                        delay(50)
                        "ok"
                    }
                }
            }
            jobs.forEach { assertEquals("ok", it.await()) }
        }

        assertEquals(1, counter.get())
    }

    @Test
    fun coalesce_differentKeys_runIndependently() = runTest {
        val coalescer = RequestCoalescer()
        val counter = AtomicInteger(0)

        coroutineScope {
            val a = async {
                coalescer.coalesce("a") {
                    counter.incrementAndGet()
                    "a"
                }
            }
            val b = async {
                coalescer.coalesce("b") {
                    counter.incrementAndGet()
                    "b"
                }
            }
            assertEquals("a", a.await())
            assertEquals("b", b.await())
        }

        assertEquals(2, counter.get())
    }

    @Test
    fun coalesce_propagatesFailure() = runTest {
        val coalescer = RequestCoalescer()
        val result = runCatching {
            coalescer.coalesce("fail") { throw IllegalStateException("boom") }
        }
        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun coalesce_sequentialCallsAfterCompletion_runAgain() = runTest {
        val coalescer = RequestCoalescer()
        val counter = AtomicInteger(0)

        val first = coalescer.coalesce("seq") {
            counter.incrementAndGet()
            "first"
        }
        val second = coalescer.coalesce("seq") {
            counter.incrementAndGet()
            "second"
        }

        assertEquals("first", first)
        assertEquals("second", second)
        assertEquals(2, counter.get())
    }

    @Test
    fun clear_allowsFreshExecution() = runTest {
        val coalescer = RequestCoalescer()
        val counter = AtomicInteger(0)

        coalescer.coalesce("k") { counter.incrementAndGet() }
        coalescer.clear()
        coalescer.coalesce("k") { counter.incrementAndGet() }

        assertEquals(2, counter.get())
    }

    @Test
    fun coalesce_returnsTypedResult() = runTest {
        val coalescer = RequestCoalescer()
        val value = coalescer.coalesce("typed") { listOf(1, 2, 3) }
        assertEquals(listOf(1, 2, 3), value)
    }

    @Test
    fun coalesce_manyConcurrentWaiters_shareSingleResult() = runTest {
        val coalescer = RequestCoalescer()
        val counter = AtomicInteger(0)

        val results = coroutineScope {
            List(12) {
                async {
                    coalescer.coalesce("many") {
                        counter.incrementAndGet()
                        delay(30)
                        42
                    }
                }
            }.map { it.await() }
        }

        assertTrue(results.all { it == 42 })
        assertEquals(1, counter.get())
    }
}
