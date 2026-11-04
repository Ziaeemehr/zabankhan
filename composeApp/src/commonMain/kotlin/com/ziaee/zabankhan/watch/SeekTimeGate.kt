package com.ziaee.zabankhan.watch

/** Reject samples queued before a seek and the iframe's cached position until the seek lands. */
class SeekTimeGate(private val clock: () -> Long) {
    var request: Int = 0
        private set
    private var target: Long? = null
    private var requestedAt = 0L
    fun seekTo(ms: Long) { request++; target = ms.coerceAtLeast(0); requestedAt = clock() }
    fun acceptTime(sampleSeekRequest: Int, ms: Long): Long? {
        if (sampleSeekRequest != request || ms < 0) return null
        val pending = target ?: return ms
        return when {
            ms >= pending && ms - pending <= 600L -> { target = null; ms }
            clock() - requestedAt >= 2_000L -> { target = null; ms }
            ms < pending && pending - ms <= 100L -> pending
            else -> null
        }
    }
}
