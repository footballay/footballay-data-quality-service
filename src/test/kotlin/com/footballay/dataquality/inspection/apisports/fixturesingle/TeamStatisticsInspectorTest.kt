package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TeamStatisticsInspectorTest {
    private val inspector = TeamStatisticsInspector()
    private val objectMapper = ObjectMapper()

    @Test
    fun `allows complete known statistics with consistent numeric relations`() {
        val issues =
            inspector.inspect(
                root(
                    group(1, "55%", "10", "8", "10", "5", "3", "7", "3", "2", "2.1"),
                    group(2, "45%", "8", "6", "8", "4", "2", "5", "3", "2", "0.8"),
                ),
            )

        assertThat(issues).isEmpty()
    }

    @Test
    fun `reports group structure violations using valid fixture team references`() {
        val issues =
            inspector.inspect(
                root(
                    group(1),
                    group(1),
                    group(3),
                ),
            )

        assertThat(issues.map { it.inspectionRuleCode.value })
            .containsExactlyInAnyOrder(
                "TEAM_STATISTICS_COUNT_INVALID",
                "TEAM_STATISTICS_DUPLICATE_TEAM",
                "TEAM_STATISTICS_UNKNOWN_TEAM",
                "TEAM_STATISTICS_MISSING_FIXTURE_TEAM",
            )
    }

    @Test
    fun `reports malformed types values and intra group numeric relations`() {
        val statistics =
            listOf(
                statistic("Total Shots", "3"),
                statistic("Shots on Goal", "4"),
                statistic("Shots off Goal", "1"),
                statistic("Blocked Shots", "1"),
                statistic("Shots insidebox", "2"),
                statistic("Shots outsidebox", "2"),
                statistic("Total passes", "10"),
                statistic("Passes accurate", "12"),
                statistic("Passes %", "bad"),
                statistic("expected_goals", "-0.1"),
                statistic("Fouls", "-1"),
                statistic("Provider Change", "1"),
                statistic(null, "1"),
            )
        val issues = inspector.inspect(root(group(1, statistics = statistics), group(2)))

        assertThat(issues.map { it.inspectionRuleCode.value })
            .contains(
                "SHOT_COMPONENT_EXCEEDS_TOTAL",
                "SHOT_LOCATION_TOTAL_MISMATCH",
                "SHOT_OUTCOME_TOTAL_MISMATCH",
                "ACCURATE_PASSES_EXCEED_TOTAL_PASSES",
                "PERCENTAGE_FORMAT_INVALID",
                "EXPECTED_GOALS_INVALID",
                "STATISTIC_NEGATIVE_VALUE",
                "UNKNOWN_STATISTIC_TYPE",
                "STATISTIC_TYPE_MISSING",
            )
    }

    @Test
    fun `allows negative goals prevented and ignores null statistics arrays`() {
        val issues = inspector.inspect(root(group(1, statistics = listOf(statistic("goals_prevented", "-2"))), ApiSportsFixtureSingleTeamStatisticsDto(team = ApiSportsFixtureSingleEventTeamDto(id = 2), statistics = null)))

        assertThat(issues).isEmpty()
    }

    @Test
    fun `allows explicit JSON null values for optional percentage and expected goals statistics`() {
        val optionalNulls = listOf(statisticJson("Ball Possession", "null"), statisticJson("Passes %", "null"), statisticJson("expected_goals", "null"))

        assertThat(inspector.inspect(root(group(1, statistics = optionalNulls), group(2, statistics = optionalNulls)))).isEmpty()
    }

    @Test
    fun `parses numeric and string count values from provider JSON`() {
        val numericValueIssues = inspector.inspect(root(group(1, statistics = listOf(statisticJson("Total Shots", "3"), statistic("Shots on Goal", "4"))), group(2)))
        val stringValueIssues = inspector.inspect(root(group(1, statistics = listOf(statistic("Total Shots", "3"), statisticJson("Shots on Goal", "4"))), group(2)))

        assertThat(numericValueIssues.map { it.inspectionRuleCode.value }).containsExactly("SHOT_COMPONENT_EXCEEDS_TOTAL")
        assertThat(stringValueIssues.map { it.inspectionRuleCode.value }).containsExactly("SHOT_COMPONENT_EXCEEDS_TOTAL")
    }

    @Test
    fun `skips possession total when statistics groups do not exactly match fixture teams`() {
        val duplicateTeams = inspector.inspect(root(group(1, "70%"), group(1, "70%")))
        val unknownTeam = inspector.inspect(root(group(1, "70%"), group(3, "70%")))
        val missingFixtureTeam = inspector.inspect(root(group(1, "70%"), group(1, "30%")))

        assertThat(duplicateTeams.map { it.inspectionRuleCode.value }).contains("TEAM_STATISTICS_DUPLICATE_TEAM").doesNotContain("POSSESSION_TOTAL_INVALID")
        assertThat(unknownTeam.map { it.inspectionRuleCode.value }).contains("TEAM_STATISTICS_UNKNOWN_TEAM").doesNotContain("POSSESSION_TOTAL_INVALID")
        assertThat(missingFixtureTeam.map { it.inspectionRuleCode.value }).contains("TEAM_STATISTICS_MISSING_FIXTURE_TEAM").doesNotContain("POSSESSION_TOTAL_INVALID")
    }

    @Test
    fun `skips shot and pass relations when their required statistic type is duplicated`() {
        val shotIssues = inspector.inspect(root(group(1, statistics = listOf(statistic("Total Shots", "10"), statistic("Total Shots", "3"), statistic("Shots on Goal", "4"))), group(2)))
        val passIssues = inspector.inspect(root(group(1, statistics = listOf(statistic("Total passes", "10"), statistic("Passes accurate", "11"), statistic("Passes accurate", "1"), statistic("Passes %", "90%"))), group(2)))

        assertThat(shotIssues.map { it.inspectionRuleCode.value }).contains("STATISTIC_TYPE_DUPLICATED").doesNotContain("SHOT_COMPONENT_EXCEEDS_TOTAL", "SHOT_LOCATION_TOTAL_MISMATCH", "SHOT_OUTCOME_TOTAL_MISMATCH")
        assertThat(passIssues.map { it.inspectionRuleCode.value }).contains("STATISTIC_TYPE_DUPLICATED").doesNotContain("ACCURATE_PASSES_EXCEED_TOTAL_PASSES", "PASSES_PERCENT_INCONSISTENT")
    }

    @Test
    fun `applies possession percentage boundaries and total tolerance`() {
        val permitted = inspector.inspect(root(group(1, "0%"), group(2, "100%")))
        val outsideTotal = inspector.inspect(root(group(1, "60%"), group(2, "43%")))
        val aboveRange = inspector.inspect(root(group(1, "101%"), group(2, "0%")))
        val belowRange = inspector.inspect(root(group(1, "-1%"), group(2, "100%")))

        assertThat(permitted).isEmpty()
        assertThat(outsideTotal.map { it.inspectionRuleCode.value }).containsExactly("POSSESSION_TOTAL_INVALID")
        assertThat(aboveRange.map { it.inspectionRuleCode.value }).contains("PERCENTAGE_OUT_OF_RANGE")
        assertThat(belowRange.map { it.inspectionRuleCode.value }).contains("PERCENTAGE_OUT_OF_RANGE")
    }

    @Test
    fun `allows one percentage point pass difference and reports larger differences`() {
        val tolerated = inspector.inspect(root(group(1, statistics = listOf(statistic("Total passes", "100"), statistic("Passes accurate", "80"), statistic("Passes %", "81%"))), group(2)))
        val inconsistent = inspector.inspect(root(group(1, statistics = listOf(statistic("Total passes", "100"), statistic("Passes accurate", "80"), statistic("Passes %", "82%"))), group(2)))

        assertThat(tolerated.map { it.inspectionRuleCode.value }).doesNotContain("PASSES_PERCENT_INCONSISTENT")
        assertThat(inconsistent.map { it.inspectionRuleCode.value }).contains("PASSES_PERCENT_INCONSISTENT")
    }

    @Test
    fun `uses fixture status to set missing fixture team severity`() {
        val beforeKickoff = inspector.inspect(root(group(1), statusShort = "NS")).single { it.inspectionRuleCode.value == "TEAM_STATISTICS_MISSING_FIXTURE_TEAM" }
        val started = inspector.inspect(root(group(1), statusShort = "1H")).single { it.inspectionRuleCode.value == "TEAM_STATISTICS_MISSING_FIXTURE_TEAM" }
        val finished = inspector.inspect(root(group(1), statusShort = "FT")).single { it.inspectionRuleCode.value == "TEAM_STATISTICS_MISSING_FIXTURE_TEAM" }

        assertThat(beforeKickoff.severity.name).isEqualTo("WARN")
        assertThat(started.severity.name).isEqualTo("ERROR")
        assertThat(finished.severity.name).isEqualTo("ERROR")
    }

    @Test
    fun `allows a valid string expected goals value`() {
        val issues = inspector.inspect(root(group(1, statistics = listOf(statisticJson("expected_goals", "\"1.94\""))), group(2)))

        assertThat(issues).isEmpty()
    }

    private fun root(vararg groups: ApiSportsFixtureSingleTeamStatisticsDto, statusShort: String? = null): ApiSportsFixtureSingleRootDto =
        ApiSportsFixtureSingleRootDto(
            response =
                listOf(
                    ApiSportsFixtureSingleResponseDto(
                        teams = ApiSportsFixtureSingleTeamsDto(home = ApiSportsFixtureSingleTeamDto(id = 1), away = ApiSportsFixtureSingleTeamDto(id = 2)),
                        fixture = ApiSportsFixtureSingleFixtureDto(status = ApiSportsFixtureSingleStatusDto(short = statusShort)),
                        statistics = groups.toList(),
                    ),
                ),
        )

    private fun group(
        teamId: Long,
        possession: String? = null,
        totalPasses: String? = null,
        accuratePasses: String? = null,
        totalShots: String? = null,
        onTarget: String? = null,
        offTarget: String? = null,
        insideBox: String? = null,
        outsideBox: String? = null,
        blocked: String? = null,
        expectedGoals: String? = null,
        statistics: List<ApiSportsFixtureSingleStatisticDto?>? = null,
    ): ApiSportsFixtureSingleTeamStatisticsDto =
        ApiSportsFixtureSingleTeamStatisticsDto(
            team = ApiSportsFixtureSingleEventTeamDto(id = teamId),
            statistics =
                statistics
                    ?: listOfNotNull(
                        possession?.let { statistic("Ball Possession", it) },
                        totalPasses?.let { statistic("Total passes", it) },
                        accuratePasses?.let { statistic("Passes accurate", it) },
                        totalShots?.let { statistic("Total Shots", it) },
                        onTarget?.let { statistic("Shots on Goal", it) },
                        offTarget?.let { statistic("Shots off Goal", it) },
                        insideBox?.let { statistic("Shots insidebox", it) },
                        outsideBox?.let { statistic("Shots outsidebox", it) },
                        blocked?.let { statistic("Blocked Shots", it) },
                        expectedGoals?.let { statistic("expected_goals", it) },
                    ),
        )

    private fun statistic(type: String?, value: String): ApiSportsFixtureSingleStatisticDto =
        ApiSportsFixtureSingleStatisticDto(type = type, value = objectMapper.readTree("\"$value\""))

    private fun statisticJson(type: String?, value: String): ApiSportsFixtureSingleStatisticDto =
        ApiSportsFixtureSingleStatisticDto(type = type, value = objectMapper.readTree(value))
}
