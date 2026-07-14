package com.footballay.footballaydataqualityservice.pipeline

fun interface RawEventIdempotencyChecker {
    fun shouldProcess(rawEventId: String): Boolean
}

class NoopRawEventIdempotencyChecker : RawEventIdempotencyChecker {
    override fun shouldProcess(rawEventId: String): Boolean = true
}
