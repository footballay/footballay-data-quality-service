package com.footballay.footballaydataqualityservice.inspection.apisports.fixturesingle

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.footballaydataqualityservice.inspection.FoundIssue
import com.footballay.footballaydataqualityservice.inspection.InspectionEvidence
import com.footballay.footballaydataqualityservice.inspection.InspectionCodeGroup
import com.footballay.footballaydataqualityservice.inspection.InspectionResponseLocation
import com.footballay.footballaydataqualityservice.inspection.InspectionSeverity
import com.footballay.footballaydataqualityservice.inspection.QualityInspector
import com.footballay.footballaydataqualityservice.pipeline.input.DataProvider
import com.footballay.footballaydataqualityservice.pipeline.input.QualityCheckRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import java.util.zip.GZIPInputStream

class ApiSportsFixtureSingleInspectionProcessorTest {
    private val objectMapper = ObjectMapper()
    private val testCodeGroup = InspectionCodeGroup("TEST", "FIXTURE_SINGLE")
    private val testRootIssue = testCodeGroup.define("TEST_ROOT_ISSUE")
    private val testScalarIssue = testCodeGroup.define("TEST_SCALAR_ISSUE")

    @Test
    fun `collects issues from inspectors for the fixture single root DTO`() {
        val inspector =
            QualityInspector<ApiSportsFixtureSingleRootDto> { target ->
                if (target.get == "fixtures") {
                    listOf(
                        FoundIssue(
                            inspectionCode = testRootIssue,
                            severity = InspectionSeverity.INFO,
                            title = "Test root issue",
                            responseLocation = InspectionResponseLocation("root", "\$.get"),
                        ),
                    )
                } else {
                    emptyList()
                }
            }
        val processor = ApiSportsFixtureSingleInspectionProcessor(objectMapper, listOf(inspector), emptyList())

        val result = processor.inspect(request(), objectMapper.readTree("""{"get":"fixtures"}"""))

        assertThat(result.map { it.inspectionCode }).containsExactly(testRootIssue)
    }

    @Test
    fun `returns a critical issue when raw JSON cannot map to the root DTO`() {
        val processor = ApiSportsFixtureSingleInspectionProcessor(objectMapper, emptyList(), emptyList())

        val rawJson = objectMapper.readTree("[]")
        val result = processor.inspect(request(), rawJson)

        val issue = result.single()
        assertThat(issue.inspectionCode).isEqualTo(ApiSportsFixtureSingleInspectionCodes.RAW_JSON_MAPPING_FAILED)
        assertThat(issue.severity).isEqualTo(InspectionSeverity.CRITICAL)
        assertThat(issue.responseLocation).isEqualTo(InspectionResponseLocation("root", "$"))
        assertThat(issue.evidence?.values).containsKey("exceptionType")
    }

    @Test
    fun `returns a critical issue for a null root response without invoking inspectors`() {
        var invoked = false
        val inspector = QualityInspector<ApiSportsFixtureSingleRootDto> { invoked = true; emptyList() }
        val processor = ApiSportsFixtureSingleInspectionProcessor(objectMapper, listOf(inspector), emptyList())
        val rawJson = objectMapper.readTree("null")

        val result = processor.inspect(request(), rawJson)

        assertThat(result.single().inspectionCode).isEqualTo(ApiSportsFixtureSingleInspectionCodes.ROOT_RESPONSE_NULL)
        assertThat(result.single().evidence).isNull()
        assertThat(invoked).isFalse()
    }

    @Test
    fun `preserves scalar evidence supplied by an inspector`() {
        val inspector =
            QualityInspector<ApiSportsFixtureSingleRootDto> {
                listOf(
                    FoundIssue(
                        inspectionCode = testScalarIssue,
                        severity = InspectionSeverity.WARN,
                        title = "Test scalar issue",
                        responseLocation = InspectionResponseLocation("root", "$.parameters.id"),
                        evidence = InspectionEvidence.of("actualFixtureId" to "invalid"),
                    ),
                )
            }
        val processor = ApiSportsFixtureSingleInspectionProcessor(objectMapper, listOf(inspector), emptyList())

        val result = processor.inspect(request(), objectMapper.readTree("""{"parameters":{"id":"ignored"}}"""))

        assertThat(result.single().evidence?.values).containsExactly(entry("actualFixtureId", "invalid"))
    }

    @Test
    fun `skips request-response comparison when request fixture ID is invalid`() {
        val processor =
            ApiSportsFixtureSingleInspectionProcessor(
                objectMapper = objectMapper,
                responseInspectors = emptyList(),
                requestResponseInspectors = listOf(FixtureSingleRequestResponseInspector()),
            )
        val invalidRequest = request().copy(parameters = mapOf("fixtureId" to listOf("invalid")))

        val result =
            processor.inspect(
                invalidRequest,
                objectMapper.readTree("""{"response":[{"fixture":{"id":654321}}]}"""),
            )

        assertThat(result).isEmpty()
    }

    @Test
    fun `maps the API Sports fixture single gzip test resource without issues`() {
        val processor =
            ApiSportsFixtureSingleInspectionProcessor(
                objectMapper,
                listOf(
                    ApiSportsFixtureSingleResponseInspector(),
                    FixtureInspector(),
                    FixtureStatusInspector(),
                ),
                listOf(FixtureSingleRequestResponseInspector()),
            )
        val rawJson =
            ClassPathResource("mock/data-quality/mock-apisports-fixturesingle.json.gz").inputStream.use { input ->
                GZIPInputStream(input).use(objectMapper::readTree)
            }
        val result = processor.inspect(request(), rawJson)

        assertThat(result.map { it.inspectionCode })
            .containsExactly(ApiSportsFixtureSingleInspectionCodes.VENUE_ID_NON_POSITIVE)
    }

    private fun request() =
        QualityCheckRequest(
            provider = DataProvider.API_SPORTS,
            endpointKey = "fixtureSingle",
            parameters = mapOf("fixtureId" to listOf("1582681")),
        )
}
