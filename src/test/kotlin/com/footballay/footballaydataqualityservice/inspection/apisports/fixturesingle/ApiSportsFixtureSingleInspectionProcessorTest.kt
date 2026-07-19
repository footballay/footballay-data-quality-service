package com.footballay.footballaydataqualityservice.inspection.apisports.fixturesingle

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.footballaydataqualityservice.inspection.FoundIssue
import com.footballay.footballaydataqualityservice.inspection.InspectionRuleCode
import com.footballay.footballaydataqualityservice.inspection.InspectionEvidence
import com.footballay.footballaydataqualityservice.inspection.InspectionResponseLocation
import com.footballay.footballaydataqualityservice.inspection.InspectionSeverity
import com.footballay.footballaydataqualityservice.inspection.InspectionName
import com.footballay.footballaydataqualityservice.inspection.QualityInspector
import com.footballay.footballaydataqualityservice.inspection.QualityIssueType
import com.footballay.footballaydataqualityservice.pipeline.input.DataProvider
import com.footballay.footballaydataqualityservice.pipeline.input.QualityCheckRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import java.util.zip.GZIPInputStream

class ApiSportsFixtureSingleInspectionProcessorTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `collects issues from inspectors for the fixture single root DTO`() {
        val inspector =
            TestInspector<ApiSportsFixtureSingleRootDto>(InspectionName.of("ROOT")) { target ->
                if (target.get == "fixtures") {
                    listOf(
                        FoundIssue(
                            suggestedType = QualityIssueType.UNEXPECTED_RESPONSE,
                            inspectionName = InspectionName.of("ROOT"),
                            inspectionRuleCode = TestRuleCode.ROOT_ISSUE,
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

        assertThat(result.map { it.inspectionRuleCode }).containsExactly(TestRuleCode.ROOT_ISSUE)
    }

    @Test
    fun `rejects an issue whose scope differs from its inspector scope`() {
        val inspector =
            TestInspector<ApiSportsFixtureSingleRootDto>(InspectionName.of("ROOT")) {
                listOf(
                    FoundIssue(
                        suggestedType = QualityIssueType.UNEXPECTED_RESPONSE,
                        inspectionName = InspectionName.of("FIXTURE"),
                        inspectionRuleCode = TestRuleCode.ROOT_ISSUE,
                        severity = InspectionSeverity.ERROR,
                        title = "Mismatched scope issue",
                        responseLocation = InspectionResponseLocation("root", "$.get"),
                    ),
                )
            }
        val processor = ApiSportsFixtureSingleInspectionProcessor(objectMapper, listOf(inspector), emptyList())

        assertThatIllegalStateException()
            .isThrownBy { processor.inspect(request(), objectMapper.readTree("""{"get":"fixtures"}""")) }
            .withMessage(
                "Inspector returned an issue with a mismatched inspection name. " +
                    "inspectorName=ROOT, foundIssueName=FIXTURE, inspectionRuleCode=ROOT_ISSUE",
            )
    }

    @Test
    fun `returns a critical issue when raw JSON cannot map to the root DTO`() {
        val processor = ApiSportsFixtureSingleInspectionProcessor(objectMapper, emptyList(), emptyList())

        val rawJson = objectMapper.readTree("[]")
        val result = processor.inspect(request(), rawJson)

        val issue = result.single()
        assertThat(issue.inspectionName).isEqualTo(InspectionName.of("RAW_MAPPING"))
        assertThat(issue.inspectionRuleCode.value).isEqualTo("MAPPING_FAILED")
        assertThat(issue.suggestedType).isEqualTo(QualityIssueType.UPSTREAM_ERROR)
        assertThat(issue.severity).isEqualTo(InspectionSeverity.CRITICAL)
        assertThat(issue.responseLocation).isEqualTo(InspectionResponseLocation("root", "$"))
        assertThat(issue.evidence?.values).containsKey("exceptionType")
    }

    @Test
    fun `returns a critical issue for a null root response without invoking inspectors`() {
        var invoked = false
        val inspector = TestInspector<ApiSportsFixtureSingleRootDto>(InspectionName.of("ROOT")) { invoked = true; emptyList() }
        val processor = ApiSportsFixtureSingleInspectionProcessor(objectMapper, listOf(inspector), emptyList())
        val rawJson = objectMapper.readTree("null")

        val result = processor.inspect(request(), rawJson)

        assertThat(result.single().inspectionName).isEqualTo(InspectionName.of("RAW_MAPPING"))
        assertThat(result.single().inspectionRuleCode.value).isEqualTo("ROOT_NULL")
        assertThat(result.single().suggestedType).isEqualTo(QualityIssueType.UNEXPECTED_NULL)
        assertThat(result.single().evidence).isNull()
        assertThat(invoked).isFalse()
    }

    @Test
    fun `preserves scalar evidence supplied by an inspector`() {
        val inspector =
            TestInspector<ApiSportsFixtureSingleRootDto>(InspectionName.of("FIXTURE")) {
                listOf(
                    FoundIssue(
                        suggestedType = QualityIssueType.INVALID_FORMAT,
                        inspectionName = InspectionName.of("FIXTURE"),
                        inspectionRuleCode = TestRuleCode.SCALAR_ISSUE,
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

        assertThat(result.map { it.inspectionName.value to it.inspectionRuleCode.value })
            .containsExactly("FIXTURE" to "VENUE_ID_NON_POSITIVE")
    }

    private fun request() =
        QualityCheckRequest(
            provider = DataProvider.API_SPORTS,
            endpointKey = "fixtureSingle",
            parameters = mapOf("fixtureId" to listOf("1582681")),
        )

    private class TestInspector<T>(
        override val inspectionName: InspectionName,
        private val inspectBlock: (T) -> List<FoundIssue>,
    ) : QualityInspector<T> {
        override fun inspect(target: T): List<FoundIssue> = inspectBlock(target)
    }

    private enum class TestRuleCode : InspectionRuleCode {
        ROOT_ISSUE,
        SCALAR_ISSUE,
        ;

        override val value: String
            get() = name
    }

}
