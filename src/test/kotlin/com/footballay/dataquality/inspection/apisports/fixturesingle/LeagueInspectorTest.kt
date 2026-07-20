package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.dataquality.inspection.InspectionSeverity
import com.footballay.dataquality.inspection.QualityIssueType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test

class LeagueInspectorTest {
    private val objectMapper = ObjectMapper()
    private val inspector = LeagueInspector()

    @Test
    fun `allows a valid league and a scheduled fixture without a round`() {
        val issues =
            inspector.inspect(
                targetOf(
                    """
                    {"response":[{"fixture":{"status":{"short":"NS"}},"league":{"id":1,"name":"League","season":2026,"round":null}}]}
                    """.trimIndent(),
                ),
            )

        assertThat(issues).isEmpty()
    }

    @Test
    fun `reports an issue when the league object is null`() {
        val issue = inspector.inspect(targetOf("""{"response":[{"league":null}]}""")).single()

        assertThat(issue.inspectionName.value).isEqualTo("LEAGUE")
        assertThat(issue.inspectionRuleCode.value).isEqualTo("OBJECT_NULL")
        assertThat(issue.suggestedType).isEqualTo(QualityIssueType.UNEXPECTED_NULL)
        assertThat(issue.severity).isEqualTo(InspectionSeverity.CRITICAL)
        assertThat(issue.responseLocation.path).isEqualTo("$.response[0].league")
    }

    @Test
    fun `skips a null response item`() {
        val issues = inspector.inspect(targetOf("""{"response":[null]}"""))

        assertThat(issues).isEmpty()
    }

    @Test
    fun `reports null league fields after the match has started`() {
        val issues =
            inspector.inspect(
                targetOf(
                    """
                    {"response":[{"fixture":{"status":{"short":"FT"}},"league":{"id":null,"name":null,"season":null,"round":null}}]}
                    """.trimIndent(),
                ),
            )

        assertThat(issues.map { it.inspectionRuleCode.value })
            .containsExactly(
                "LEAGUE_ID_MISSING_OR_INVALID",
                "LEAGUE_NAME_MISSING",
                "LEAGUE_SEASON_INVALID",
                "LEAGUE_ROUND_MISSING",
            )
        assertThat(issues.map { it.suggestedType })
            .containsExactly(
                QualityIssueType.UNEXPECTED_NULL,
                QualityIssueType.UNEXPECTED_NULL,
                QualityIssueType.UNEXPECTED_NULL,
                QualityIssueType.UNEXPECTED_NULL,
            )
        assertThat(issues.map { it.severity })
            .containsExactly(
                InspectionSeverity.ERROR,
                InspectionSeverity.WARN,
                InspectionSeverity.WARN,
                InspectionSeverity.WARN,
            )
        assertThat(issues.last().evidence?.values).containsExactly(entry("round", null), entry("statusShort", "FT"))
    }

    @Test
    fun `reports invalid and blank league fields`() {
        val issues =
            inspector.inspect(
                targetOf(
                    """
                    {"response":[{"fixture":{"status":{"short":"1H"}},"league":{"id":0,"name":" ","season":2101,"round":" "}}]}
                    """.trimIndent(),
                ),
            )

        assertThat(issues.map { it.inspectionRuleCode.value })
            .containsExactly(
                "LEAGUE_ID_MISSING_OR_INVALID",
                "LEAGUE_NAME_MISSING",
                "LEAGUE_SEASON_INVALID",
                "LEAGUE_ROUND_MISSING",
            )
        assertThat(issues.map { it.suggestedType })
            .containsExactly(
                QualityIssueType.OUT_OF_RANGE,
                QualityIssueType.UNEXPECTED_BLANK,
                QualityIssueType.OUT_OF_RANGE,
                QualityIssueType.UNEXPECTED_BLANK,
            )
    }

    @Test
    fun `does not require a round for statuses that are not started or finished`() {
        listOf("TBD", "NS", "PST", "CANC", "AWD", "WO", "UNKNOWN").forEach { statusShort ->
            val issues =
                inspector.inspect(
                    targetOf(
                        """
                        {"response":[{"fixture":{"status":{"short":"$statusShort"}},"league":{"id":1,"name":"League","season":2026,"round":null}}]}
                        """.trimIndent(),
                    ),
                )

            assertThat(issues).isEmpty()
        }
    }

    private fun targetOf(json: String): ApiSportsFixtureSingleRootDto =
        objectMapper.treeToValue(objectMapper.readTree(json), ApiSportsFixtureSingleRootDto::class.java)
}
