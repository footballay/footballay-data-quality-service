package com.footballay.dataquality.pipeline.idempotency

/** 동일 raw event 처리 호출을 소유한 Pipeline 내부 처리권입니다. */
class RawEventIdempotencyClaim internal constructor(
    internal val rawEventId: String,
    internal val ownerToken: String,
)

interface RawEventIdempotencyChecker {
    fun tryClaim(rawEventId: String): RawEventIdempotencyClaim?

    fun complete(claim: RawEventIdempotencyClaim)

    fun release(claim: RawEventIdempotencyClaim)
}

class NoopRawEventIdempotencyChecker : RawEventIdempotencyChecker {
    override fun tryClaim(rawEventId: String): RawEventIdempotencyClaim =
        RawEventIdempotencyClaim(
            rawEventId = rawEventId,
            ownerToken = "noop",
        )

    override fun complete(claim: RawEventIdempotencyClaim) = Unit

    override fun release(claim: RawEventIdempotencyClaim) = Unit
}
