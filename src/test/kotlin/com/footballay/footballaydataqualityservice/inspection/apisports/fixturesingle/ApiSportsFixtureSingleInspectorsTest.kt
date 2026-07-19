package com.footballay.footballaydataqualityservice.inspection.apisports.fixturesingle

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.footballaydataqualityservice.inspection.InspectionSeverity
import com.footballay.footballaydataqualityservice.inspection.QualityIssueType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test

class ApiSportsFixtureSingleInspectorsTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `reports root response issues with evidence`() {
        val target =
            targetOf(
                """
                {
                  "get": "fixtures",
                  "parameters": {"id": "invalid"},
                  "errors": ["provider error"],
                  "results": 2,
                  "paging": {"current": 0, "total": 0},
                  "response": []
                }
                """.trimIndent(),
            )

        val issues = ApiSportsFixtureSingleResponseInspector().inspect(target)

        assertThat(issues.map { it.inspectionName.value }).containsOnly("ROOT")
        assertThat(issues.map { it.inspectionRuleCode.value })
            .containsExactly(
                "PARAMETER_ID_INVALID",
                "ERRORS_PRESENT",
                "PAGING_CURRENT_INVALID",
                "PAGING_TOTAL_INVALID",
                "RESULTS_RESPONSE_SIZE_MISMATCH",
                "SINGLE_FIXTURE_RESULTS_NOT_ONE",
                "RESPONSE_EMPTY",
            )
        assertThat(issues.map { it.suggestedType })
            .containsExactly(
                QualityIssueType.INVALID_FORMAT,
                QualityIssueType.UPSTREAM_ERROR,
                QualityIssueType.OUT_OF_RANGE,
                QualityIssueType.OUT_OF_RANGE,
                QualityIssueType.INCONSISTENT_INFORMATION,
                QualityIssueType.UNEXPECTED_RESPONSE,
                QualityIssueType.UNEXPECTED_RESPONSE,
            )
        assertThat(issues.first().evidence?.values).containsExactly(entry("actualFixtureId", "invalid"))
        assertThat(issues[1].evidence).isNull()
        assertThat(issues[2].evidence?.values).containsExactly(entry("actualCurrent", "0"))
    }

    @Test
    fun `reports fixture identifier and time issues without status-dependent rules`() {
        val target =
            targetOf(
                """
                {
                  "response": [{
                    "fixture": {
                      "id": 0,
                      "timezone": "",
                      "date": "invalid",
                      "timestamp": -1,
                      "periods": {"first": 20, "second": 10},
                      "referee": " ",
                      "venue": {"id": 0, "name": "", "city": ""}
                    }
                  }]
                }
                """.trimIndent(),
            )

        val issues = FixtureInspector().inspect(target)

        assertThat(issues.map { it.inspectionName.value }).containsOnly("FIXTURE")
        assertThat(issues.map { it.inspectionRuleCode.value })
            .containsExactly(
                "ID_NON_POSITIVE",
                "TIMEZONE_BLANK",
                "DATE_INVALID_FORMAT",
                "TIMESTAMP_NON_POSITIVE",
                "SECOND_PERIOD_BEFORE_FIRST",
                "REFEREE_BLANK",
                "VENUE_ID_NON_POSITIVE",
                "VENUE_NAME_BLANK",
                "VENUE_CITY_BLANK",
            )
        assertThat(issues.map { it.suggestedType })
            .containsExactly(
                QualityIssueType.OUT_OF_RANGE,
                QualityIssueType.UNEXPECTED_BLANK,
                QualityIssueType.INVALID_FORMAT,
                QualityIssueType.OUT_OF_RANGE,
                QualityIssueType.INCONSISTENT_INFORMATION,
                QualityIssueType.UNEXPECTED_BLANK,
                QualityIssueType.OUT_OF_RANGE,
                QualityIssueType.UNEXPECTED_BLANK,
                QualityIssueType.UNEXPECTED_BLANK,
            )
    }

    @Test
    fun `reports an error when the second period is before kickoff`() {
        val target =
            targetOf(
                """
                {
                  "response": [{
                    "fixture": {
                      "date": "2026-07-13T00:00:00+00:00",
                      "timestamp": 1783900800,
                      "periods": {"second": 1783900799}
                    }
                  }]
                }
                """.trimIndent(),
            )

        val findings = FixtureInspector().inspect(target)

        assertThat(findings.single { it.inspectionRuleCode.value == "SECOND_PERIOD_BEFORE_KICKOFF" }.inspectionName.value)
            .isEqualTo("FIXTURE")
        assertThat(findings.single { it.inspectionRuleCode.value == "SECOND_PERIOD_BEFORE_KICKOFF" }.severity)
            .isEqualTo(InspectionSeverity.ERROR)
    }

    @Test
    fun `reports a non-empty object errors payload without inspecting nested values`() {
        val target = targetOf("""{"errors":{"id":{"message":"must be an integer"}}}""")

        val issues = ApiSportsFixtureSingleResponseInspector().inspect(target)

        assertThat(issues.map { it.inspectionRuleCode.value })
            .contains("ERRORS_PRESENT")
        assertThat(issues.first { it.inspectionRuleCode.value == "ERRORS_PRESENT" }.evidence).isNull()
    }

    @Test
    fun `reports defined fixture status integrity issues`() {
        val target =
            targetOf(
                """
                {
                  "response": [{
                    "fixture": {
                      "status": {"long": "", "short": null, "elapsed": -1, "extra": -1}
                    }
                  }]
                }
                """.trimIndent(),
            )

        val issues = FixtureStatusInspector().inspect(target)

        assertThat(issues.map { it.inspectionName.value }).containsOnly("FIXTURE_STATUS")
        assertThat(issues.map { it.inspectionRuleCode.value })
            .containsExactly(
                "LONG_BLANK",
                "SHORT_NULL",
                "ELAPSED_NEGATIVE",
                "EXTRA_NEGATIVE",
            )
        assertThat(issues.map { it.suggestedType })
            .containsExactly(
                QualityIssueType.UNEXPECTED_BLANK,
                QualityIssueType.UNEXPECTED_NULL,
                QualityIssueType.OUT_OF_RANGE,
                QualityIssueType.OUT_OF_RANGE,
            )
    }

    @Test
    fun `reports request and response identifier mismatches`() {
        val response =
            targetOf(
                """
                {
                  "parameters": {},
                  "response": [{"fixture": {"id": 654321}}]
                }
                """.trimIndent(),
            )
        val target = FixtureSingleRequestResponse(FixtureSingleRequest(123456), response)

        val issues = FixtureSingleRequestResponseInspector().inspect(target)

        assertThat(issues.map { it.inspectionName.value }).containsOnly("REQUEST_RESPONSE")
        assertThat(issues.map { it.inspectionRuleCode.value })
            .containsExactly("PARAMETER_ID_MISSING", "FIXTURE_ID_MISMATCH")
        assertThat(issues.map { it.suggestedType })
            .containsExactly(QualityIssueType.UNEXPECTED_NULL, QualityIssueType.INCONSISTENT_INFORMATION)
    }

    @Test
    fun `reports a mismatch between request and response parameter fixture IDs`() {
        val target =
            FixtureSingleRequestResponse(
                request = FixtureSingleRequest(123456),
                response = targetOf("""{"parameters":{"id":"654321"},"response":[]}"""),
            )

        val issue = FixtureSingleRequestResponseInspector().inspect(target).single()

        assertThat(issue.inspectionName.value).isEqualTo("REQUEST_RESPONSE")
        assertThat(issue.inspectionRuleCode.value).isEqualTo("PARAMETER_ID_MISMATCH")
        assertThat(issue.suggestedType).isEqualTo(QualityIssueType.INCONSISTENT_INFORMATION)
        assertThat(issue.severity).isEqualTo(InspectionSeverity.ERROR)
        assertThat(issue.responseLocation.path).isEqualTo("$.parameters.id")
        assertThat(issue.evidence?.values)
            .containsExactly(
                entry("requestedFixtureId", "123456"),
                entry("responseParameterFixtureId", "654321"),
            )
    }

    @Test
    fun `skips request comparisons when fixture ID is unavailable`() {
        val target =
            FixtureSingleRequestResponse(
                request = FixtureSingleRequest(null),
                response = targetOf("""{"parameters":{},"response":[{"fixture":{"id":654321}}]}"""),
            )

        val issues = FixtureSingleRequestResponseInspector().inspect(target)

        assertThat(issues).isEmpty()
    }

    private fun targetOf(json: String): ApiSportsFixtureSingleRootDto {
        val rawJson = objectMapper.readTree(json)
        return objectMapper.treeToValue(rawJson, ApiSportsFixtureSingleRootDto::class.java)
    }
}
