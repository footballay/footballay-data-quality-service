package com.footballay.footballaydataqualityservice.inspection.apisports.fixturesingle

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.footballaydataqualityservice.inspection.FoundIssue
import com.footballay.footballaydataqualityservice.inspection.InspectionCode
import com.footballay.footballaydataqualityservice.inspection.InspectionEvidence
import com.footballay.footballaydataqualityservice.inspection.InspectionResponseLocation
import com.footballay.footballaydataqualityservice.inspection.InspectionSeverity
import com.footballay.footballaydataqualityservice.inspection.InspectionScope
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

    @Test
    fun `collects issues from inspectors for the fixture single root DTO`() {
        val inspector =
            TestInspector<ApiSportsFixtureSingleRootDto>(TestScope.TEST_ROOT) { target ->
                if (target.get == "fixtures") {
                    listOf(
                        FoundIssue(
                            inspectionScope = TestScope.TEST_ROOT,
                            inspectionCode = TestCode.ROOT_ISSUE,
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

        assertThat(result.map { it.inspectionCode }).containsExactly(TestCode.ROOT_ISSUE)
    }

    @Test
    fun `returns a critical issue when raw JSON cannot map to the root DTO`() {
        val processor = ApiSportsFixtureSingleInspectionProcessor(objectMapper, emptyList(), emptyList())

        val rawJson = objectMapper.readTree("[]")
        val result = processor.inspect(request(), rawJson)

        val issue = result.single()
        assertThat(issue.inspectionScope).isEqualTo(FixtureSingleInspectionScope.RAW_MAPPING)
        assertThat(issue.inspectionCode.value).isEqualTo("MAPPING_FAILED")
        assertThat(issue.severity).isEqualTo(InspectionSeverity.CRITICAL)
        assertThat(issue.responseLocation).isEqualTo(InspectionResponseLocation("root", "$"))
        assertThat(issue.evidence?.values).containsKey("exceptionType")
    }

    @Test
    fun `returns a critical issue for a null root response without invoking inspectors`() {
        var invoked = false
        val inspector = TestInspector<ApiSportsFixtureSingleRootDto>(TestScope.TEST_ROOT) { invoked = true; emptyList() }
        val processor = ApiSportsFixtureSingleInspectionProcessor(objectMapper, listOf(inspector), emptyList())
        val rawJson = objectMapper.readTree("null")

        val result = processor.inspect(request(), rawJson)

        assertThat(result.single().inspectionScope).isEqualTo(FixtureSingleInspectionScope.RAW_MAPPING)
        assertThat(result.single().inspectionCode.value).isEqualTo("ROOT_NULL")
        assertThat(result.single().evidence).isNull()
        assertThat(invoked).isFalse()
    }

    @Test
    fun `preserves scalar evidence supplied by an inspector`() {
        val inspector =
            TestInspector<ApiSportsFixtureSingleRootDto>(TestScope.TEST_SCALAR) {
                listOf(
                    FoundIssue(
                        inspectionScope = TestScope.TEST_SCALAR,
                        inspectionCode = TestCode.SCALAR_ISSUE,
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

        assertThat(result.map { it.inspectionScope.value to it.inspectionCode.value })
            .containsExactly("FIXTURE" to "VENUE_ID_NON_POSITIVE")
    }

    @Test
    fun `rejects duplicated inspection scopes during processor initialization`() {
        val first = TestInspector<ApiSportsFixtureSingleRootDto>(TestScope.TEST_ROOT) { emptyList() }
        val second = TestInspector<ApiSportsFixtureSingleRootDto>(TestScope.TEST_ROOT) { emptyList() }

        org.assertj.core.api.Assertions.assertThatIllegalStateException()
            .isThrownBy { ApiSportsFixtureSingleInspectionProcessor(objectMapper, listOf(first, second), emptyList()) }
            .withMessage("Duplicated inspection scopes for APISPORTS:FIXTURE_SINGLE: TEST_ROOT")
    }

    @Test
    fun `allows different inspection scopes during processor initialization`() {
        val responseInspector = TestInspector<ApiSportsFixtureSingleRootDto>(TestScope.TEST_ROOT) { emptyList() }
        val requestInspector = TestInspector<FixtureSingleRequestResponse>(TestScope.TEST_SCALAR) { emptyList() }

        val processor = ApiSportsFixtureSingleInspectionProcessor(objectMapper, listOf(responseInspector), listOf(requestInspector))

        assertThat(processor).isNotNull
    }

    private fun request() =
        QualityCheckRequest(
            provider = DataProvider.API_SPORTS,
            endpointKey = "fixtureSingle",
            parameters = mapOf("fixtureId" to listOf("1582681")),
        )

    private class TestInspector<T>(
        override val inspectionScope: InspectionScope,
        private val inspectBlock: (T) -> List<FoundIssue>,
    ) : QualityInspector<T> {
        override fun inspect(target: T): List<FoundIssue> = inspectBlock(target)
    }

    private enum class TestScope : InspectionScope {
        TEST_ROOT,
        TEST_SCALAR,
        ;

        override val value: String
            get() = name
    }

    private enum class TestCode : InspectionCode {
        ROOT_ISSUE,
        SCALAR_ISSUE,
        ;

        override val value: String
            get() = name
    }

}
