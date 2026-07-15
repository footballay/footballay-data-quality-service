package com.footballay.footballaydataqualityservice.inspection

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.footballay.footballaydataqualityservice.pipeline.DataProvider
import com.footballay.footballaydataqualityservice.pipeline.QualityScanResult
import com.footballay.footballaydataqualityservice.pipeline.RawCollectedEvent
import com.footballay.footballaydataqualityservice.pipeline.QualityParameter
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test
import java.time.Instant

class ProcessorRoutingQualityCheckerTest {
    @Test
    fun `routes an event to its matching processor`() {
        var called = false
        var supportsCalled = false
        val checker =
            ProcessorRoutingQualityChecker(
                listOf(
                    processor(supports = { provider, endpointKey ->
                        supportsCalled = true
                        provider == DataProvider.API_SPORTS && endpointKey == "fixtureSingle"
                    }) {
                        called = true
                        QualityScanResult("test")
                    },
                ),
            )

        val result = checker.check(rawEvent(), JsonNodeFactory.instance.objectNode())

        assertThat(called).isTrue()
        assertThat(supportsCalled).isTrue()
        assertThat(result.scannerVersion).isEqualTo("test")
    }

    @Test
    fun `fails when no processor matches the event`() {
        val checker = ProcessorRoutingQualityChecker(emptyList())

        assertThatIllegalStateException()
            .isThrownBy { checker.check(rawEvent(), JsonNodeFactory.instance.objectNode()) }
            .withMessageContaining("found 0")
    }

    @Test
    fun `fails when multiple processors match the event`() {
        val checker =
            ProcessorRoutingQualityChecker(
                listOf(
                    processor(supports = { _, _ -> true }) { QualityScanResult("first") },
                    processor(supports = { _, _ -> true }) { QualityScanResult("second") },
                ),
            )

        assertThatIllegalStateException()
            .isThrownBy { checker.check(rawEvent(), JsonNodeFactory.instance.objectNode()) }
            .withMessageContaining("found 2")
    }

    private fun processor(
        supports: (DataProvider, String) -> Boolean,
        inspect: () -> QualityScanResult,
    ): QualityInspectionProcessor =
        object : QualityInspectionProcessor {
            override fun supports(
                provider: DataProvider,
                endpointKey: String,
            ): Boolean = supports(provider, endpointKey)

            override fun inspect(rawJson: com.fasterxml.jackson.databind.JsonNode): QualityScanResult = inspect()
        }

    private fun rawEvent() =
        RawCollectedEvent(
            rawEventId = "01JZK8T9CJ4S9ZZ9G0E0D7YQ9M",
            provider = DataProvider.API_SPORTS,
            endpointKey = "fixtureSingle",
            parameters = listOf(QualityParameter("fixtureId", "123456")),
            canonicalHash = "sha256-base64url",
            rawJsonObjectKey = "football-data/raw/example.json.gz",
            collectedAt = Instant.parse("2026-07-12T00:00:00Z"),
        )
}
