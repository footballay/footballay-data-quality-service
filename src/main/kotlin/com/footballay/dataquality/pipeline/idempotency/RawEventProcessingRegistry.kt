package com.footballay.dataquality.pipeline.idempotency

/** 동일 raw event 처리 호출을 소유한 Pipeline 내부 처리권입니다. */
class RawEventProcessingClaim internal constructor(
    internal val rawEventId: String,
    internal val ownerToken: String,
)

interface RawEventProcessingRegistry {
    fun tryClaim(rawEventId: String): RawEventProcessingClaim?

    fun complete(claim: RawEventProcessingClaim)

    fun release(claim: RawEventProcessingClaim)
}

class NoopRawEventProcessingRegistry : RawEventProcessingRegistry {
    override fun tryClaim(rawEventId: String): RawEventProcessingClaim =
        RawEventProcessingClaim(
            rawEventId = rawEventId,
            ownerToken = "noop",
        )

    override fun complete(claim: RawEventProcessingClaim) = Unit

    override fun release(claim: RawEventProcessingClaim) = Unit
}
