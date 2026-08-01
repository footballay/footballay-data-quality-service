package com.footballay.dataquality.pipeline.idempotency

import com.github.benmanes.caffeine.cache.Ticker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class CaffeineRawEventProcessingRegistryTest {
    @Test
    fun `rejects a second claim while the first claim is in progress`() {
        val checker = checker()

        assertThat(checker.tryClaim("raw-event-1")).isNotNull
        assertThat(checker.tryClaim("raw-event-1")).isNull()
    }

    @Test
    fun `rejects a completed claim until its ttl expires`() {
        val ticker = MutableTicker()
        val checker = checker(ticker = ticker)
        val claim = requireNotNull(checker.tryClaim("raw-event-1"))

        checker.complete(claim)

        assertThat(checker.tryClaim("raw-event-1")).isNull()

        ticker.advance(Duration.ofSeconds(1))

        assertThat(checker.tryClaim("raw-event-1")).isNotNull
    }

    @Test
    fun `allows retry after the owner releases an in-progress claim`() {
        val checker = checker()
        val claim = requireNotNull(checker.tryClaim("raw-event-1"))

        checker.release(claim)

        assertThat(checker.tryClaim("raw-event-1")).isNotNull
    }

    @Test
    fun `does not let an old claim release a newer claim or completed entry`() {
        val checker = checker()
        val oldClaim = requireNotNull(checker.tryClaim("raw-event-1"))
        checker.release(oldClaim)
        val currentClaim = requireNotNull(checker.tryClaim("raw-event-1"))

        checker.release(oldClaim)

        assertThat(checker.tryClaim("raw-event-1")).isNull()

        checker.complete(currentClaim)
        checker.release(currentClaim)

        assertThat(checker.tryClaim("raw-event-1")).isNull()
    }

    private fun checker(
        ticker: Ticker = Ticker.systemTicker(),
    ) =
        CaffeineRawEventProcessingRegistry(
            ttl = Duration.ofSeconds(1),
            maximumSize = 10,
            ticker = ticker,
        )

    private class MutableTicker : Ticker {
        private var nanos: Long = 0

        override fun read(): Long = nanos

        fun advance(duration: Duration) {
            nanos += duration.toNanos()
        }
    }
}
