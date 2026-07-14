package com.footballay.footballaydataqualityservice.pipeline

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DataQualityPipelineServiceTest {
    @Test
    fun `processes a new raw event in pipeline order and publishes a no-issue result`() {
        val calls = mutableListOf<String>()
        var published: QualityResult? = null
        val pipeline =
            DataQualityPipelineService(
                idempotencyChecker = {
                    calls += "idempotency"
                    true
                },
                rawResponseReader = {
                    calls += "reader"
                    JsonNodeFactory.instance.objectNode()
                },
                qualityChecker = { _, _ ->
                    calls += "checker"
                    QualityScanResult("always-happy-v1")
                },
                resultStore = {
                    calls += "store"
                    it
                },
                resultPublisher = {
                    calls += "publisher"
                    published = it
                },
                clock = Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneOffset.UTC),
            )

        pipeline.process(rawEvent())

        assertThat(calls).containsExactly("idempotency", "reader", "checker", "store", "publisher")
        assertThat(published).isNotNull
        assertThat(published!!.resultId).matches("[0-9A-HJKMNP-TV-Z]{26}")
        assertThat(published!!.hasIssue).isFalse()
        assertThat(published!!.issueCount).isZero()
        assertThat(published!!.maxSeverity).isEqualTo(QualityMaxSeverity.NONE)
        assertThat(published!!.checkStatus).isEqualTo(QualityCheckStatus.NO_ISSUE)
    }

    @Test
    fun `does not continue when idempotency checker rejects the event`() {
        var readCalled = false
        val pipeline =
            DataQualityPipelineService(
                idempotencyChecker = { false },
                rawResponseReader = {
                    readCalled = true
                    JsonNodeFactory.instance.objectNode()
                },
                qualityChecker = AlwaysHappyQualityChecker(),
                resultStore = NoopQualityResultStore(),
                resultPublisher = { },
                clock = Clock.systemUTC(),
            )

        pipeline.process(rawEvent())

        assertThat(readCalled).isFalse()
    }

    private fun rawEvent() =
        RawCollectedEvent(
            rawEventId = "01JZK8T9CJ4S9ZZ9G0E0D7YQ9M",
            provider = "API_SPORTS",
            endpointKey = "fixtureSingle",
            parameters = listOf(QualityParameter("fixtureId", "123456")),
            canonicalHash = "sha256-base64url",
            rawJsonObjectKey = "football-data/raw/example.json.gz",
            collectedAt = Instant.parse("2026-07-12T00:00:00Z"),
        )
}
