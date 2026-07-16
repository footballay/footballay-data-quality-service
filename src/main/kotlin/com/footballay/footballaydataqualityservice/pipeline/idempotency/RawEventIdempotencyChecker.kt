package com.footballay.footballaydataqualityservice.pipeline.idempotency

fun interface RawEventIdempotencyChecker {
    fun shouldProcess(rawEventId: String): Boolean
}

class NoopRawEventIdempotencyChecker : RawEventIdempotencyChecker {
    override fun shouldProcess(rawEventId: String): Boolean = true
}
