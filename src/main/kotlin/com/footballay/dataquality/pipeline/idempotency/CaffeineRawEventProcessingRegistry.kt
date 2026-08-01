package com.footballay.dataquality.pipeline.idempotency

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import java.time.Duration
import java.util.UUID

/**
 * 단일 인스턴스 서버에서 raw event의 처리 상태와 처리권을 관리합니다.
 * TTL 이내에는 처리 중이거나 완료된 event의 재처리를 막고, TTL 이후에는 재처리를 허용합니다.
 */
class CaffeineRawEventProcessingRegistry(
    ttl: Duration,
    maximumSize: Long,
    ticker: Ticker = Ticker.systemTicker(),
) : RawEventProcessingRegistry {
    private val entries: Cache<String, Entry> =
        Caffeine
            .newBuilder()
            .expireAfterWrite(ttl)
            .maximumSize(maximumSize)
            .ticker(ticker)
            .build()

    override fun tryClaim(rawEventId: String): RawEventProcessingClaim? {
        val claim =
            RawEventProcessingClaim(
                rawEventId = rawEventId,
                ownerToken = UUID.randomUUID().toString(),
            )
        val previous = entries.asMap().putIfAbsent(rawEventId, InProgress(claim.ownerToken))
        return claim.takeIf { previous == null }
    }

    override fun complete(claim: RawEventProcessingClaim) {
        entries.asMap().replace(claim.rawEventId, InProgress(claim.ownerToken), Completed)
    }

    override fun release(claim: RawEventProcessingClaim) {
        entries.asMap().remove(claim.rawEventId, InProgress(claim.ownerToken))
    }

    private sealed interface Entry

    private data class InProgress(
        val ownerToken: String,
    ) : Entry

    private data object Completed : Entry
}
