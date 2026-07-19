package com.footballay.footballaydataqualityservice.inspection

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.footballay.footballaydataqualityservice.pipeline.input.DataProvider
import com.footballay.footballaydataqualityservice.pipeline.input.QualityCheckRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test

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
                        emptyList()
                    },
                ),
            )

        val result = checker.check(request(), JsonNodeFactory.instance.objectNode())

        assertThat(called).isTrue()
        assertThat(supportsCalled).isTrue()
        assertThat(result).isEmpty()
    }

    @Test
    fun `fails when no processor matches the event`() {
        val checker = ProcessorRoutingQualityChecker(emptyList())

        assertThatIllegalStateException()
            .isThrownBy { checker.check(request(), JsonNodeFactory.instance.objectNode()) }
            .withMessageContaining("found 0")
    }

    @Test
    fun `fails when multiple processors match the event`() {
        val checker =
            ProcessorRoutingQualityChecker(
                listOf(
                    processor(supports = { _, _ -> true }) { emptyList() },
                    processor(supports = { _, _ -> true }) { emptyList() },
                ),
            )

        assertThatIllegalStateException()
            .isThrownBy { checker.check(request(), JsonNodeFactory.instance.objectNode()) }
            .withMessageContaining("found 2")
    }

    private fun processor(
        supports: (DataProvider, String) -> Boolean,
        inspect: () -> List<FoundIssue>,
    ): QualityInspectionProcessor =
        object : QualityInspectionProcessor {
            override fun inspectors(): List<QualityInspector<*>> = emptyList()

            override fun supports(
                provider: DataProvider,
                endpointKey: String,
            ): Boolean = supports(provider, endpointKey)

            override fun inspect(
                request: QualityCheckRequest,
                rawJson: com.fasterxml.jackson.databind.JsonNode,
            ): List<FoundIssue> = inspect()
        }

    private fun request() =
        QualityCheckRequest(
            provider = DataProvider.API_SPORTS,
            endpointKey = "fixtureSingle",
            parameters = mapOf("fixtureId" to listOf("123456")),
        )
}
