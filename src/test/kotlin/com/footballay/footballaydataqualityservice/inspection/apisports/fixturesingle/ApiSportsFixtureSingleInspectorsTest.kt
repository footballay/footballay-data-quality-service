package com.footballay.footballaydataqualityservice.inspection.apisports.fixturesingle

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.footballaydataqualityservice.inspection.InspectionSeverity
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

        assertThat(issues.map { it.suggestedTypeCode })
            .containsExactly(
                "PARAMETER_FIXTURE_ID_INVALID",
                "ERRORS_PRESENT",
                "PAGING_CURRENT_INVALID",
                "PAGING_TOTAL_INVALID",
                "RESULTS_RESPONSE_SIZE_MISMATCH",
                "SINGLE_FIXTURE_RESULTS_NOT_ONE",
                "RESPONSE_EMPTY",
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

        assertThat(issues.map { it.suggestedTypeCode })
            .containsExactly(
                "FIXTURE_ID_NON_POSITIVE",
                "FIXTURE_TIMEZONE_BLANK",
                "FIXTURE_DATE_INVALID_FORMAT",
                "FIXTURE_TIMESTAMP_NON_POSITIVE",
                "SECOND_PERIOD_BEFORE_FIRST",
                "REFEREE_BLANK",
                "VENUE_ID_NON_POSITIVE",
                "VENUE_NAME_BLANK",
                "VENUE_CITY_BLANK",
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

        assertThat(findings.single { it.suggestedTypeCode == "SECOND_PERIOD_BEFORE_KICKOFF" }.severity)
            .isEqualTo(InspectionSeverity.ERROR)
    }

    @Test
    fun `reports a non-empty object errors payload without inspecting nested values`() {
        val target = targetOf("""{"errors":{"id":{"message":"must be an integer"}}}""")

        val issues = ApiSportsFixtureSingleResponseInspector().inspect(target)

        assertThat(issues.map { it.suggestedTypeCode })
            .contains("ERRORS_PRESENT")
        assertThat(issues.first { it.suggestedTypeCode == "ERRORS_PRESENT" }.evidence).isNull()
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

        assertThat(issues.map { it.suggestedTypeCode })
            .containsExactly(
                "STATUS_LONG_BLANK",
                "STATUS_SHORT_NULL",
                "STATUS_ELAPSED_NEGATIVE",
                "STATUS_EXTRA_NEGATIVE",
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

        assertThat(issues.map { it.suggestedTypeCode })
            .containsExactly("PARAMETER_FIXTURE_ID_MISSING", "FIXTURE_ID_REQUEST_MISMATCH")
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
