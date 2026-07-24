package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.footballay.dataquality.inspection.InspectionSeverity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlayerStatisticsInspectorTest {
    private val inspector = PlayerStatisticsInspector()

    @Test
    fun `allows valid player statistics including nullable rating`() {
        val issues =
            inspector.inspect(
                root(
                    group(1, player(11, "Home", entry(position = "G", rating = null, shots = ApiSportsFixtureSinglePlayerShotsDto(total = 1, on = 1), passes = ApiSportsFixtureSinglePlayerPassesDto(total = 20, accuracy = 10)))),
                    group(2, player(21, "Away", entry(position = "F", shots = ApiSportsFixtureSinglePlayerShotsDto(total = 2, on = 1), duels = ApiSportsFixtureSinglePlayerDuelsDto(total = 4, won = 2), dribbles = ApiSportsFixtureSinglePlayerDribblesDto(attempts = 3, success = 2)))),
                ),
            )

        assertThat(issues).isEmpty()
    }

    @Test
    fun `reports player group and player identity violations`() {
        val issues = inspector.inspect(root(group(1, player(null, null), player(3, "A"), player(3, "B")), group(3, player(4, "Unknown"))))

        assertThat(issues.map { it.inspectionRuleCode.value })
            .contains(
                "PLAYER_STATISTICS_UNKNOWN_TEAM",
                "PLAYER_STATISTICS_MISSING_FIXTURE_TEAM",
                "PLAYER_ID_MISSING",
                "PLAYER_NAME_MISSING",
                "PLAYER_STATISTICS_LIST_NULL",
                "PLAYER_DUPLICATED_IN_TEAM",
            )
    }

    @Test
    fun `distinguishes null empty and null entry player statistics lists`() {
        val nullList = player(11, "Null list")
        val emptyList = ApiSportsFixtureSinglePlayerStatisticsDto(player = ApiSportsFixtureSinglePlayerStatisticsPlayerDto(id = 12, name = "Empty list"), statistics = emptyList())
        val nullEntry = ApiSportsFixtureSinglePlayerStatisticsDto(player = ApiSportsFixtureSinglePlayerStatisticsPlayerDto(id = 13, name = "Null entry"), statistics = listOf(null))

        val issues = inspector.inspect(root(group(1, nullList, emptyList, nullEntry), group(2, player(21, "Away", entry()))))

        assertThat(issues.map { it.inspectionRuleCode.value })
            .containsExactlyInAnyOrder(
                "PLAYER_STATISTICS_LIST_NULL",
                "PLAYER_STATISTICS_LIST_EMPTY",
                "PLAYER_STATISTICS_ENTRY_OBJECT_NULL",
            )
    }

    @Test
    fun `reports invalid games fields and player statistic relationships`() {
        val invalidEntry =
            entry(
                minutes = 141,
                number = 0,
                position = "X",
                rating = "invalid",
                shots = ApiSportsFixtureSinglePlayerShotsDto(total = 1, on = 2),
                passes = ApiSportsFixtureSinglePlayerPassesDto(total = 1, accuracy = 2),
                duels = ApiSportsFixtureSinglePlayerDuelsDto(total = 1, won = 2),
                dribbles = ApiSportsFixtureSinglePlayerDribblesDto(attempts = 1, success = 2),
                goals = ApiSportsFixtureSinglePlayerGoalsDto(saves = 1),
                offsides = -1,
            )
        val issues = inspector.inspect(root(group(1, player(11, "Home", invalidEntry)), group(2, player(21, "Away", entry()))))

        assertThat(issues.map { it.inspectionRuleCode.value })
            .contains(
                "PLAYER_MINUTES_INVALID",
                "PLAYER_NUMBER_INVALID",
                "PLAYER_POSITION_UNKNOWN",
                "PLAYER_RATING_INVALID",
                "NEGATIVE_PLAYER_STAT_VALUE",
                "SHOTS_ON_EXCEED_TOTAL",
                "PASSES_ACCURATE_EXCEED_TOTAL",
                "DUELS_WON_EXCEED_TOTAL",
                "DRIBBLES_SUCCESS_EXCEED_ATTEMPTS",
            )
    }

    @Test
    fun `reports multiple statistics entries and captains`() {
        val captainEntry = entry(captain = true)
        val issues = inspector.inspect(root(group(1, player(11, "First", captainEntry), player(12, "Second", captainEntry, entry())), group(2, player(21, "Away", entry()))))

        assertThat(issues.map { it.inspectionRuleCode.value }).contains("PLAYER_STATISTICS_ENTRY_COUNT_UNEXPECTED", "MULTIPLE_CAPTAINS_IN_TEAM")
    }

    @Test
    fun `uses fixture status to determine missing team group severity`() {
        val beforeKickoff = inspector.inspect(root(group(1, player(11, "Home", entry())), statusShort = "NS")).single { it.inspectionRuleCode.value == "PLAYER_STATISTICS_MISSING_FIXTURE_TEAM" }
        val started = inspector.inspect(root(group(1, player(11, "Home", entry())), statusShort = "1H")).single { it.inspectionRuleCode.value == "PLAYER_STATISTICS_MISSING_FIXTURE_TEAM" }

        assertThat(beforeKickoff.severity).isEqualTo(InspectionSeverity.WARN)
        assertThat(started.severity).isEqualTo(InspectionSeverity.ERROR)
    }

    @Test
    fun `allows minute and rating boundaries but rejects invalid and non finite ratings`() {
        val valid = inspector.inspect(root(group(1, player(11, "Home", entry(minutes = 120, rating = "0")), player(12, "Home 2", entry(minutes = 140, rating = "10"))), group(2, player(21, "Away", entry(rating = null)))))
        val invalid = inspector.inspect(root(group(1, player(11, "Home", entry(minutes = -1, rating = "10.1")), player(12, "Home 2", entry(rating = "NaN")), player(13, "Home 3", entry(rating = " "))), group(2, player(21, "Away", entry()))))

        assertThat(valid).isEmpty()
        assertThat(invalid.map { it.inspectionRuleCode.value }).contains("PLAYER_MINUTES_INVALID", "PLAYER_RATING_INVALID")
    }

    @Test
    fun `allows nullable games and optional action statistics without coercing them to zero`() {
        val nonParticipant = ApiSportsFixtureSinglePlayerStatisticEntryDto(games = null, shots = null, passes = null, duels = null, dribbles = null)
        val issues = inspector.inspect(root(group(1, player(11, "Home", nonParticipant)), group(2, player(21, "Away", entry(shots = ApiSportsFixtureSinglePlayerShotsDto(total = null, on = null))))) )

        assertThat(issues).isEmpty()
    }

    @Test
    fun `reports null objects within non empty player statistics arrays and still inspects entries`() {
        val entry = entry(shots = ApiSportsFixtureSinglePlayerShotsDto(total = 1, on = 2))
        val playerWithoutReference = ApiSportsFixtureSinglePlayerStatisticsDto(player = null, statistics = listOf(entry, null))
        val group = ApiSportsFixtureSinglePlayerStatisticsTeamDto(team = ApiSportsFixtureSingleEventTeamDto(id = 1), players = listOf(null, playerWithoutReference))
        val root = ApiSportsFixtureSingleRootDto(response = listOf(ApiSportsFixtureSingleResponseDto(teams = ApiSportsFixtureSingleTeamsDto(home = ApiSportsFixtureSingleTeamDto(id = 1), away = ApiSportsFixtureSingleTeamDto(id = 2)), players = listOf(null, group))))

        assertThat(inspector.inspect(root).map { it.inspectionRuleCode.value })
            .contains(
                "PLAYER_STATISTICS_TEAM_GROUP_OBJECT_NULL",
                "PLAYER_STATISTICS_PLAYER_OBJECT_NULL",
                "PLAYER_STATISTICS_PLAYER_REFERENCE_OBJECT_NULL",
                "PLAYER_STATISTICS_ENTRY_OBJECT_NULL",
                "SHOTS_ON_EXCEED_TOTAL",
            )
    }

    @Test
    fun `does not use negative or missing relation values as comparison bases and includes comparison evidence`() {
        val negative = inspector.inspect(root(group(1, player(11, "Home", entry(shots = ApiSportsFixtureSinglePlayerShotsDto(total = -1, on = 0)))), group(2, player(21, "Away", entry()))))
        val equalAndMissing = inspector.inspect(root(group(1, player(11, "Home", entry(shots = ApiSportsFixtureSinglePlayerShotsDto(total = 2, on = 2))), player(12, "Home 2", entry(shots = ApiSportsFixtureSinglePlayerShotsDto(total = null, on = 2))), player(13, "Home 3", entry(shots = ApiSportsFixtureSinglePlayerShotsDto(total = 2, on = 1)))), group(2, player(21, "Away", entry()))))
        val exceeds = inspector.inspect(root(group(1, player(11, "Home", entry(shots = ApiSportsFixtureSinglePlayerShotsDto(total = 1, on = 2)))), group(2, player(21, "Away", entry())))).single { it.inspectionRuleCode.value == "SHOTS_ON_EXCEED_TOTAL" }

        assertThat(negative.map { it.inspectionRuleCode.value }).contains("NEGATIVE_PLAYER_STAT_VALUE").doesNotContain("SHOTS_ON_EXCEED_TOTAL")
        assertThat(equalAndMissing.map { it.inspectionRuleCode.value }).doesNotContain("SHOTS_ON_EXCEED_TOTAL")
        assertThat(exceeds.evidence?.values).containsEntry("shotsOn", "2").containsEntry("shotsTotal", "1")
    }

    @Test
    fun `does not classify unknown positions as outfield but detects known outfield saves`() {
        val unknown = inspector.inspect(root(group(1, player(11, "Home", entry(position = "X", goals = ApiSportsFixtureSinglePlayerGoalsDto(saves = 1)))), group(2, player(21, "Away", entry()))))
        val outfield = inspector.inspect(root(group(1, player(11, "Home", entry(position = "D", goals = ApiSportsFixtureSinglePlayerGoalsDto(saves = 1)))), group(2, player(21, "Away", entry()))))
        val goalkeeper = inspector.inspect(root(group(1, player(11, "Home", entry(position = "G", goals = ApiSportsFixtureSinglePlayerGoalsDto(saves = 1)))), group(2, player(21, "Away", entry()))))

        assertThat(unknown.map { it.inspectionRuleCode.value }).containsExactly("PLAYER_POSITION_UNKNOWN")
        assertThat(outfield.map { it.inspectionRuleCode.value }).containsExactly("OUTFIELD_PLAYER_GOALKEEPER_STAT_INVALID")
        assertThat(goalkeeper).isEmpty()
    }

    @Test
    fun `reports both unknown and missing fixture teams with missing team evidence`() {
        val issue = inspector.inspect(root(group(3, player(31, "Unknown 1", entry())), group(4, player(41, "Unknown 2", entry())))).single { it.inspectionRuleCode.value == "PLAYER_STATISTICS_MISSING_FIXTURE_TEAM" }

        assertThat(inspector.inspect(root(group(3, player(31, "Unknown 1", entry())), group(4, player(41, "Unknown 2", entry())))).map { it.inspectionRuleCode.value }).contains("PLAYER_STATISTICS_UNKNOWN_TEAM", "PLAYER_STATISTICS_MISSING_FIXTURE_TEAM")
        assertThat(issue.evidence?.values).containsEntry("missingTeamIds", "1,2")
    }

    private fun root(vararg groups: ApiSportsFixtureSinglePlayerStatisticsTeamDto, statusShort: String? = null): ApiSportsFixtureSingleRootDto =
        ApiSportsFixtureSingleRootDto(
            response =
                listOf(
                    ApiSportsFixtureSingleResponseDto(
                        fixture = ApiSportsFixtureSingleFixtureDto(status = ApiSportsFixtureSingleStatusDto(short = statusShort)),
                        teams = ApiSportsFixtureSingleTeamsDto(home = ApiSportsFixtureSingleTeamDto(id = 1), away = ApiSportsFixtureSingleTeamDto(id = 2)),
                        players = groups.toList(),
                    ),
                ),
        )

    private fun group(teamId: Long, vararg players: ApiSportsFixtureSinglePlayerStatisticsDto): ApiSportsFixtureSinglePlayerStatisticsTeamDto =
        ApiSportsFixtureSinglePlayerStatisticsTeamDto(team = ApiSportsFixtureSingleEventTeamDto(id = teamId), players = players.toList())

    private fun player(id: Long?, name: String?, vararg entries: ApiSportsFixtureSinglePlayerStatisticEntryDto?): ApiSportsFixtureSinglePlayerStatisticsDto =
        ApiSportsFixtureSinglePlayerStatisticsDto(player = ApiSportsFixtureSinglePlayerStatisticsPlayerDto(id = id, name = name), statistics = entries.toList().ifEmpty { null })

    private fun entry(
        minutes: Long? = 90,
        number: Long? = 1,
        position: String? = "M",
        rating: String? = "7.0",
        captain: Boolean? = false,
        offsides: Long? = null,
        shots: ApiSportsFixtureSinglePlayerShotsDto? = null,
        goals: ApiSportsFixtureSinglePlayerGoalsDto? = null,
        passes: ApiSportsFixtureSinglePlayerPassesDto? = null,
        duels: ApiSportsFixtureSinglePlayerDuelsDto? = null,
        dribbles: ApiSportsFixtureSinglePlayerDribblesDto? = null,
    ): ApiSportsFixtureSinglePlayerStatisticEntryDto =
        ApiSportsFixtureSinglePlayerStatisticEntryDto(
            games = ApiSportsFixtureSinglePlayerGamesDto(minutes = minutes, number = number, position = position, rating = rating, captain = captain),
            offsides = offsides,
            shots = shots,
            goals = goals,
            passes = passes,
            duels = duels,
            dribbles = dribbles,
        )
}
