package com.footballay.dataquality.inspection.apisports.fixtureschedule

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.footballay.dataquality.inspection.FoundIssue
import com.footballay.dataquality.inspection.ProcessorRoutingQualityChecker
import com.footballay.dataquality.inspection.QualityInspectionProcessor
import com.footballay.dataquality.inspection.QualityInspector
import com.footballay.dataquality.pipeline.input.DataProvider
import com.footballay.dataquality.pipeline.input.QualityCheckRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiSportsFixtureScheduleInspectionProcessorTest {
    private val objectMapper = ObjectMapper()
    private val processor = ApiSportsFixtureScheduleInspectionProcessor(objectMapper)

    @Test
    fun `maps a fixtures of league response without issues`() {
        val issues =
            processor.inspect(
                request("fixturesOfLeague"),
                objectMapper.readTree("""{"response":[{"fixture":{"id":123}}]}"""),
            )

        assertThat(issues).isEmpty()
    }

    @Test
    fun `reports malformed fixtures of league response`() {
        val issues = processor.inspect(request("fixturesOfLeague"), objectMapper.readTree("[]"))

        assertThat(issues.single().inspectionRuleCode.value).isEqualTo("MAPPING_FAILED")
    }

    @Test
    fun `routes fixtures of league without affecting fixture single`() {
        val fixtureSingleProcessor =
            object : QualityInspectionProcessor {
                override fun inspectors(): List<QualityInspector<*>> = emptyList()

                override fun supports(provider: DataProvider, endpointKey: String): Boolean =
                    provider == DataProvider.API_SPORTS && endpointKey == "fixtureSingle"

                override fun inspect(request: QualityCheckRequest, rawJson: JsonNode): List<FoundIssue> = emptyList()
            }
        val checker = ProcessorRoutingQualityChecker(listOf(fixtureSingleProcessor, processor))

        assertThat(checker.check(request("fixturesOfLeague"), objectMapper.readTree("""{"response":[]}"""))).isEmpty()
        assertThat(checker.check(request("fixtureSingle"), JsonNodeFactory.instance.objectNode())).isEmpty()
    }

    private fun request(endpointKey: String) =
        QualityCheckRequest(
            provider = DataProvider.API_SPORTS,
            endpointKey = endpointKey,
            parameters = emptyMap(),
        )
}
