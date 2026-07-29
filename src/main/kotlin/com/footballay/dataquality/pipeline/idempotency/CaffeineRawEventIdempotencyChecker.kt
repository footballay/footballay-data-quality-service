package com.footballay.dataquality.pipeline.idempotency

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import java.time.Duration
import java.util.UUID

/**
 * 단일 인스턴스 서버에서 사용할 Caffeine 기반 중복 검사입니다. TTL 이내의 중복 처리를 방지합니다.
 */
class CaffeineRawEventIdempotencyChecker(
    ttl: Duration,
    maximumSize: Long,
    ticker: Ticker = Ticker.systemTicker(),
) : RawEventIdempotencyChecker {
    private val entries: Cache<String, Entry> =
        Caffeine
            .newBuilder()
            .expireAfterWrite(ttl)
            .maximumSize(maximumSize)
            .ticker(ticker)
            .build()

    override fun tryClaim(rawEventId: String): RawEventIdempotencyClaim? {
        val claim =
            RawEventIdempotencyClaim(
                rawEventId = rawEventId,
                ownerToken = UUID.randomUUID().toString(),
            )
        val previous = entries.asMap().putIfAbsent(rawEventId, InProgress(claim.ownerToken))
        return claim.takeIf { previous == null }
    }

    override fun complete(claim: RawEventIdempotencyClaim) {
        entries.asMap().replace(claim.rawEventId, InProgress(claim.ownerToken), Completed)
    }

    override fun release(claim: RawEventIdempotencyClaim) {
        entries.asMap().remove(claim.rawEventId, InProgress(claim.ownerToken))
    }

    private sealed interface Entry

    private data class InProgress(
        val ownerToken: String,
    ) : Entry

    private data object Completed : Entry
}
