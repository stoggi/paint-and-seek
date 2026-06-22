package io.github.stoggi.paintandseek.net

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-player budget on inbound paint packets.
 *
 * Every accepted skin patch/snapshot/pose is stored and then rebroadcast to all
 * players tracking the sender, so one inbound packet fans out to N outbound ones.
 * A modified client could exploit that for an amplification flood; this caps how
 * many messages and bytes a single player can spend per second. Legitimate
 * painting (small dirty-rect strokes plus the occasional full snapshot) stays
 * well under the limits; anything beyond is silently dropped.
 *
 * Receivers run on the server thread, so contention is nil; the map is concurrent
 * (and the bucket synchronized) only as cheap defence in depth.
 */
object PaintRateLimiter {
    private const val WINDOW_NANOS = 1_000_000_000L // 1 second
    private const val MAX_MESSAGES_PER_WINDOW = 120
    private const val MAX_BYTES_PER_WINDOW = 1 shl 20 // 1 MiB

    private class Bucket {
        var windowStart = 0L
        var messages = 0
        var bytes = 0
    }

    private val buckets = ConcurrentHashMap<UUID, Bucket>()

    /** Charge [bytes] to [uuid]'s current window and report whether it stays within budget. */
    fun allow(uuid: UUID, bytes: Int): Boolean {
        val now = System.nanoTime()
        val bucket = buckets.getOrPut(uuid) { Bucket() }
        synchronized(bucket) {
            if (now - bucket.windowStart > WINDOW_NANOS) {
                bucket.windowStart = now
                bucket.messages = 0
                bucket.bytes = 0
            }
            bucket.messages++
            bucket.bytes += bytes
            return bucket.messages <= MAX_MESSAGES_PER_WINDOW && bucket.bytes <= MAX_BYTES_PER_WINDOW
        }
    }

    /** Forget a player's budget (call on disconnect). */
    fun clear(uuid: UUID) {
        buckets.remove(uuid)
    }
}
