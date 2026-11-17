package com.example.vpn.engine

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class BufferPoolStats(
    val hits: Long,
    val misses: Long,
    val smallPooled: Int,
    val mediumPooled: Int,
    val largePooled: Int
)

/**
 * Lock-free tiered ByteArray buffer pool for high-throughput VPN TUN & socket I/O.
 *
 * Eliminates repeated 2KB/16KB/32KB heap allocations and GC pressure inside packet read/write
 * loops while keeping total pooled memory strictly bounded.
 */
object PacketBufferPool {

    const val SMALL_CAPACITY = 2048
    const val MEDIUM_CAPACITY = 16384
    const val LARGE_CAPACITY = 32768

    private const val MAX_SMALL_BUFFERS = 64
    private const val MAX_MEDIUM_BUFFERS = 32
    private const val MAX_LARGE_BUFFERS = 16

    private val smallPool = ConcurrentLinkedQueue<ByteArray>()
    private val mediumPool = ConcurrentLinkedQueue<ByteArray>()
    private val largePool = ConcurrentLinkedQueue<ByteArray>()

    private val smallCount = AtomicInteger(0)
    private val mediumCount = AtomicInteger(0)
    private val largeCount = AtomicInteger(0)

    private val poolHits = AtomicLong(0L)
    private val poolMisses = AtomicLong(0L)

    fun acquire(minCapacity: Int): ByteArray {
        return when {
            minCapacity <= SMALL_CAPACITY -> acquireFrom(smallPool, smallCount, SMALL_CAPACITY)
            minCapacity <= MEDIUM_CAPACITY -> acquireFrom(mediumPool, mediumCount, MEDIUM_CAPACITY)
            minCapacity <= LARGE_CAPACITY -> acquireFrom(largePool, largeCount, LARGE_CAPACITY)
            else -> {
                poolMisses.incrementAndGet()
                ByteArray(minCapacity)
            }
        }
    }

    fun release(buffer: ByteArray?) {
        if (buffer == null) return
        when (buffer.size) {
            SMALL_CAPACITY -> releaseTo(buffer, smallPool, smallCount, MAX_SMALL_BUFFERS)
            MEDIUM_CAPACITY -> releaseTo(buffer, mediumPool, mediumCount, MAX_MEDIUM_BUFFERS)
            LARGE_CAPACITY -> releaseTo(buffer, largePool, largeCount, MAX_LARGE_BUFFERS)
        }
    }

    inline fun <T> useBuffer(minCapacity: Int, block: (ByteArray) -> T): T {
        val buf = acquire(minCapacity)
        return try {
            block(buf)
        } finally {
            release(buf)
        }
    }

    fun stats(): BufferPoolStats {
        return BufferPoolStats(
            hits = poolHits.get(),
            misses = poolMisses.get(),
            smallPooled = smallCount.get().coerceAtLeast(0),
            mediumPooled = mediumCount.get().coerceAtLeast(0),
            largePooled = largeCount.get().coerceAtLeast(0)
        )
    }

    fun clear() {
        smallPool.clear()
        mediumPool.clear()
        largePool.clear()
        smallCount.set(0)
        mediumCount.set(0)
        largeCount.set(0)
        poolHits.set(0L)
        poolMisses.set(0L)
    }

    private fun acquireFrom(
        queue: ConcurrentLinkedQueue<ByteArray>,
        counter: AtomicInteger,
        capacity: Int
    ): ByteArray {
        val reused = queue.poll()
        if (reused != null) {
            counter.decrementAndGet()
            poolHits.incrementAndGet()
            return reused
        }
        poolMisses.incrementAndGet()
        return ByteArray(capacity)
    }

    private fun releaseTo(
        buffer: ByteArray,
        queue: ConcurrentLinkedQueue<ByteArray>,
        counter: AtomicInteger,
        maxCount: Int
    ) {
        while (true) {
            val current = counter.get()
            if (current >= maxCount) return
            if (counter.compareAndSet(current, current + 1)) {
                queue.offer(buffer)
                return
            }
        }
    }
}
