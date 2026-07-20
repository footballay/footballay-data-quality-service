package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.dataquality.inspection.InspectionSeverity
import com.footballay.dataquality.inspection.QualityIssueType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test

class TeamsInspectorTest {
    private val objectMapper = ObjectMapper()
    private val inspector = TeamsInspector()

    @Test
    fun `allows valid teams and nullable winner values`() {
        val issues =
            inspector.inspect(
                targetOf(
                    """
                    {"response":[{"teams":{"home":{"id":1,"name":"Home","winner":null},"away":{"id":2,"name":"Away","winner":false}}}]}
                    """.trimIndent(),
                ),
            )

        assertThat(issues).isEmpty()
    }

    @Test
    fun `reports only an object null issue when teams is null`() {
        val issues = inspector.inspect(targetOf("""{"response":[{"teams":null}]}"""))

        assertThat(issues.map { it.inspectionRuleCode.value }).containsExactly("OBJECT_NULL")
        assertThat(issues.single().inspectionName.value).isEqualTo("TEAMS")
        assertThat(issues.single().suggestedType).isEqualTo(QualityIssueType.UNEXPECTED_NULL)
        assertThat(issues.single().severity).isEqualTo(InspectionSeverity.CRITICAL)
    }

    @Test
    fun `skips a null response item`() {
        val issues = inspector.inspect(targetOf("""{"response":[null]}"""))

        assertThat(issues).isEmpty()
    }

    @Test
    fun `reports missing home and away team objects`() {
        val issues = inspector.inspect(targetOf("""{"response":[{"teams":{"home":null,"away":null}}]}"""))

        assertThat(issues.map { it.inspectionRuleCode.value }).containsExactly("HOME_TEAM_MISSING", "AWAY_TEAM_MISSING")
        assertThat(issues.map { it.suggestedType }).containsOnly(QualityIssueType.UNEXPECTED_NULL)
        assertThat(issues.map { it.severity }).containsOnly(InspectionSeverity.ERROR)
        assertThat(issues.first().evidence?.values).containsExactly(entry("side", "HOME"))
    }

    @Test
    fun `reports invalid team identifiers and missing team names for each side`() {
        val issues =
            inspector.inspect(
                targetOf(
                    """
                    {"response":[{"teams":{"home":{"id":0,"name":" "},"away":{"id":null,"name":null}}}]}
                    """.trimIndent(),
                ),
            )

        assertThat(issues.map { it.inspectionRuleCode.value })
            .containsExactly(
                "TEAM_ID_MISSING_OR_INVALID",
                "TEAM_NAME_MISSING",
                "TEAM_ID_MISSING_OR_INVALID",
                "TEAM_NAME_MISSING",
            )
        assertThat(issues.map { it.suggestedType })
            .containsExactly(
                QualityIssueType.OUT_OF_RANGE,
                QualityIssueType.UNEXPECTED_BLANK,
                QualityIssueType.UNEXPECTED_NULL,
                QualityIssueType.UNEXPECTED_NULL,
            )
        assertThat(issues.first().evidence?.values).containsExactly(entry("side", "HOME"), entry("teamId", "0"))
    }

    @Test
    fun `reports a duplicated team ID and both teams marked as winners`() {
        val issues =
            inspector.inspect(
                targetOf(
                    """
                    {"response":[{"teams":{"home":{"id":1,"name":"Home","winner":true},"away":{"id":1,"name":"Away","winner":true}}}]}
                    """.trimIndent(),
                ),
            )

        assertThat(issues.map { it.inspectionRuleCode.value })
            .containsExactly("SAME_TEAM_ON_BOTH_SIDES", "BOTH_TEAMS_MARKED_WINNER")
        assertThat(issues.map { it.suggestedType }).containsOnly(QualityIssueType.INCONSISTENT_INFORMATION)
    }

    @Test
    fun `does not compare equal invalid team IDs`() {
        val issues =
            inspector.inspect(
                targetOf(
                    """
                    {"response":[{"teams":{"home":{"id":0,"name":"Home"},"away":{"id":0,"name":"Away"}}}]}
                    """.trimIndent(),
                ),
            )

        assertThat(issues.map { it.inspectionRuleCode.value })
            .containsExactly("TEAM_ID_MISSING_OR_INVALID", "TEAM_ID_MISSING_OR_INVALID")
    }

    @Test
    fun `reports team issues for every response item`() {
        val issues =
            inspector.inspect(
                targetOf(
                    """
                    {"response":[{"teams":{"home":null,"away":{"id":2,"name":"Away"}}},{"teams":{"home":null,"away":{"id":3,"name":"Away"}}}]}
                    """.trimIndent(),
                ),
            )

        assertThat(issues.map { it.responseLocation.path })
            .containsExactly("$.response[0].teams.home", "$.response[1].teams.home")
    }

    private fun targetOf(json: String): ApiSportsFixtureSingleRootDto =
        objectMapper.treeToValue(objectMapper.readTree(json), ApiSportsFixtureSingleRootDto::class.java)
}
