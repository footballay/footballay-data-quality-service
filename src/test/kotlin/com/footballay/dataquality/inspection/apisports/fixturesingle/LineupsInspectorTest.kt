package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.footballay.dataquality.inspection.InspectionSeverity
import com.footballay.dataquality.inspection.QualityIssueType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LineupsInspectorTest {
    private val inspector = LineupsInspector()

    @Test
    fun `allows unavailable lineups and a valid lineup`() {
        assertThat(inspector.inspect(target(lineups = null))).isEmpty()
        assertThat(inspector.inspect(target(lineups = emptyList()))).isEmpty()
        assertThat(inspector.inspect(target(lineups = listOf(validLineup(1), validLineup(2))))).isEmpty()
    }

    @Test
    fun `reports a null lineup object`() {
        val issues = inspector.inspect(target(lineups = listOf(null)))

        assertThat(issues.map { it.inspectionRuleCode.value }).containsExactly("LINEUP_TEAM_COUNT_INVALID", "LINEUP_OBJECT_NULL")
        assertThat(issues.last().suggestedType).isEqualTo(QualityIssueType.UNEXPECTED_NULL)
        assertThat(issues.last().severity).isEqualTo(InspectionSeverity.ERROR)
        assertThat(issues.last().responseLocation.path).isEqualTo("$.response[0].lineups[0]")
    }

    @Test
    fun `reports lineup structure issues with status dependent severity`() {
        val preMatchIssues = inspector.inspect(target(status = "NS", lineups = listOf(validLineup(1))))
        val startedIssues = inspector.inspect(target(status = "FT", lineups = listOf(validLineup(1))))

        assertThat(preMatchIssues.map { it.inspectionRuleCode.value }).containsExactly("LINEUP_TEAM_COUNT_INVALID", "LINEUP_MISSING_FIXTURE_TEAM")
        assertThat(preMatchIssues.map { it.severity }).containsOnly(InspectionSeverity.WARN)
        assertThat(startedIssues.map { it.inspectionRuleCode.value }).containsExactly("LINEUP_TEAM_COUNT_INVALID", "LINEUP_MISSING_FIXTURE_TEAM")
        assertThat(startedIssues.map { it.severity }).containsOnly(InspectionSeverity.ERROR)
    }

    @Test
    fun `reports unknown and duplicate lineup teams`() {
        val issues = inspector.inspect(target(lineups = listOf(validLineup(3), validLineup(3))))

        assertThat(issues.map { it.inspectionRuleCode.value })
            .containsExactly("LINEUP_UNKNOWN_TEAM", "LINEUP_UNKNOWN_TEAM", "LINEUP_DUPLICATE_TEAM")
    }

    @Test
    fun `reports a missing Darwin Nunez starting player ID without unrelated player issues`() {
        val players = validStartingPlayers().toMutableList()
        players[1] = player(id = null, name = "Darwin Núñez", number = 99, pos = "F", grid = "2:1")
        val lineup = validLineup(1).copy(startXI = players.map(::entry))

        val issues = inspector.inspect(target(lineups = listOf(lineup, validLineup(2))))

        assertThat(issues.map { it.inspectionRuleCode.value }).containsExactly("STARTING_PLAYER_ID_MISSING")
        assertThat(issues.single().suggestedType).isEqualTo(QualityIssueType.UNEXPECTED_NULL)
        assertThat(issues.single().responseLocation.path).isEqualTo("$.response[0].lineups[0].startXI[1].player.id")
    }

    @Test
    fun `reports formation and starting player field violations`() {
        val invalid = player(id = 0, name = " ", number = null, pos = "X", grid = "0:1")
        val lineup = validLineup(1).copy(formation = "4-foo", startXI = listOf(entry(invalid)))

        val issues = inspector.inspect(target(status = "FT", lineups = listOf(lineup, validLineup(2))))

        assertThat(issues.map { it.inspectionRuleCode.value })
            .containsExactly(
                "LINEUP_FORMATION_INVALID_FORMAT",
                "STARTING_XI_COUNT_INVALID",
                "STARTING_PLAYER_ID_MISSING",
                "STARTING_PLAYER_NAME_MISSING",
                "STARTING_PLAYER_NUMBER_MISSING",
                "STARTING_PLAYER_POSITION_UNKNOWN",
                "STARTING_PLAYER_GRID_INVALID_FORMAT",
            )
        assertThat(issues.map { it.suggestedType })
            .contains(QualityIssueType.INVALID_FORMAT, QualityIssueType.OUT_OF_RANGE, QualityIssueType.UNEXPECTED_BLANK, QualityIssueType.UNEXPECTED_NULL, QualityIssueType.UNEXPECTED_RESPONSE)
    }

    @Test
    fun `reports duplicate starting player and grid values`() {
        val players = validStartingPlayers().toMutableList()
        players[10] = players[9].copy(number = 100, grid = "5:1")
        val lineup = validLineup(1).copy(startXI = players.map(::entry))

        val issues = inspector.inspect(target(lineups = listOf(lineup, validLineup(2))))

        assertThat(issues.map { it.inspectionRuleCode.value })
            .containsExactly("STARTING_PLAYER_DUPLICATED", "STARTING_PLAYER_GRID_DUPLICATED")
    }

    @Test
    fun `reports missing and invalid lineup team IDs without using them for fixture team relations`() {
        val missingTeam = validLineup(1).copy(team = null)
        val invalidTeam = validLineup(2).copy(team = ApiSportsFixtureSingleLineupTeamDto(id = 0))

        val issues = inspector.inspect(target(lineups = listOf(missingTeam, invalidTeam)))

        assertThat(issues.map { it.inspectionRuleCode.value })
            .containsExactly("LINEUP_TEAM_ID_MISSING_OR_INVALID", "LINEUP_TEAM_ID_MISSING_OR_INVALID")
        assertThat(issues.map { it.suggestedType })
            .containsExactly(QualityIssueType.UNEXPECTED_NULL, QualityIssueType.OUT_OF_RANGE)
    }

    @Test
    fun `reports a missing or empty starting XI with status dependent severity`() {
        val missing = validLineup(1).copy(startXI = null)
        val empty = validLineup(1).copy(startXI = emptyList())

        val preMatchIssues = inspector.inspect(target(status = "NS", lineups = listOf(missing, validLineup(2))))
        val startedIssues = inspector.inspect(target(status = "FT", lineups = listOf(empty, validLineup(2))))

        assertThat(preMatchIssues.map { it.inspectionRuleCode.value }).containsExactly("STARTING_XI_MISSING_OR_EMPTY")
        assertThat(preMatchIssues.single().suggestedType).isEqualTo(QualityIssueType.UNEXPECTED_NULL)
        assertThat(preMatchIssues.single().severity).isEqualTo(InspectionSeverity.WARN)
        assertThat(startedIssues.map { it.inspectionRuleCode.value }).containsExactly("STARTING_XI_MISSING_OR_EMPTY")
        assertThat(startedIssues.single().suggestedType).isEqualTo(QualityIssueType.INCONSISTENT_INFORMATION)
        assertThat(startedIssues.single().severity).isEqualTo(InspectionSeverity.ERROR)
    }

    @Test
    fun `reports one object null issue without player field issues`() {
        val nullEntryPlayers: MutableList<ApiSportsFixtureSingleLineupPlayerEntryDto?> = validStartingPlayers().map(::entry).toMutableList()
        nullEntryPlayers[0] = null
        val nullPlayerPlayers = validStartingPlayers().map(::entry).toMutableList()
        nullPlayerPlayers[0] = ApiSportsFixtureSingleLineupPlayerEntryDto(player = null)

        val nullEntryIssues = inspector.inspect(target(lineups = listOf(validLineup(1).copy(startXI = nullEntryPlayers), validLineup(2))))
        val nullPlayerIssues = inspector.inspect(target(lineups = listOf(validLineup(1).copy(startXI = nullPlayerPlayers), validLineup(2))))
        val nullSubstituteIssues = inspector.inspect(target(lineups = listOf(validLineup(1).copy(substitutes = listOf(null)), validLineup(2))))

        assertThat(nullEntryIssues.map { it.inspectionRuleCode.value }).containsExactly("STARTING_PLAYER_OBJECT_NULL")
        assertThat(nullPlayerIssues.map { it.inspectionRuleCode.value }).containsExactly("STARTING_PLAYER_OBJECT_NULL")
        assertThat(nullSubstituteIssues.map { it.inspectionRuleCode.value }).containsExactly("SUBSTITUTE_PLAYER_OBJECT_NULL")
    }

    @Test
    fun `skips goalkeeper count when starting XI preconditions are not met`() {
        val incomplete = validLineup(1).copy(startXI = listOf(entry(validStartingPlayers().first())))
        val unknownPositionPlayers = validStartingPlayers().toMutableList()
        unknownPositionPlayers[0] = unknownPositionPlayers[0].copy(pos = "X")
        val unknownPosition = validLineup(1).copy(startXI = unknownPositionPlayers.map(::entry))

        val incompleteIssues = inspector.inspect(target(status = "FT", lineups = listOf(incomplete, validLineup(2))))
        val unknownPositionIssues = inspector.inspect(target(lineups = listOf(unknownPosition, validLineup(2))))

        assertThat(incompleteIssues.map { it.inspectionRuleCode.value }).containsExactly("STARTING_XI_COUNT_INVALID")
        assertThat(unknownPositionIssues.map { it.inspectionRuleCode.value }).containsExactly("STARTING_PLAYER_POSITION_UNKNOWN")
    }

    @Test
    fun `distinguishes invalid player numbers and validates substitute positions`() {
        val startingPlayers = validStartingPlayers().toMutableList()
        startingPlayers[0] = startingPlayers[0].copy(number = null)
        startingPlayers[1] = startingPlayers[1].copy(number = 0)
        val substitutes = listOf(entry(player(20, "Missing Number", null, "D", null)), entry(player(21, "Invalid Number", 0, "X", null)), entry(player(22, "Missing Position", 22, null, null)))
        val lineup = validLineup(1).copy(startXI = startingPlayers.map(::entry), substitutes = substitutes)

        val issues = inspector.inspect(target(lineups = listOf(lineup, validLineup(2))))

        assertThat(issues.map { it.inspectionRuleCode.value })
            .containsExactly(
                "STARTING_PLAYER_NUMBER_MISSING",
                "STARTING_PLAYER_NUMBER_MISSING",
                "SUBSTITUTE_PLAYER_NUMBER_MISSING",
                "SUBSTITUTE_PLAYER_NUMBER_MISSING",
                "SUBSTITUTE_PLAYER_POSITION_UNKNOWN",
                "SUBSTITUTE_PLAYER_POSITION_MISSING",
            )
        assertThat(issues.map { it.suggestedType })
            .containsExactly(
                QualityIssueType.UNEXPECTED_NULL,
                QualityIssueType.OUT_OF_RANGE,
                QualityIssueType.UNEXPECTED_NULL,
                QualityIssueType.OUT_OF_RANGE,
                QualityIssueType.UNEXPECTED_RESPONSE,
                QualityIssueType.UNEXPECTED_NULL,
            )
    }

    @Test
    fun `reports substitute and squad consistency issues`() {
        val substitutes = listOf(entry(player(1, "Starter", 1, "G", "1:1")), entry(player(1, "Starter Again", 1, "G", null)), entry(player(null, null, 2, "D", null)))
        val lineup = validLineup(1).copy(substitutes = substitutes)

        val issues = inspector.inspect(target(lineups = listOf(lineup, validLineup(2))))

        assertThat(issues.map { it.inspectionRuleCode.value })
            .containsExactly(
                "SUBSTITUTE_GRID_PRESENT",
                "SUBSTITUTE_PLAYER_ID_MISSING",
                "SUBSTITUTE_PLAYER_NAME_MISSING",
                "SUBSTITUTE_PLAYER_DUPLICATED",
                "PLAYER_IN_START_AND_SUBSTITUTES",
                "DUPLICATE_SQUAD_NUMBER",
                "DUPLICATE_SQUAD_NUMBER",
            )
    }

    @Test
    fun `reports coach and color violations only when the optional objects are present`() {
        val lineup =
            validLineup(1).copy(
                team = ApiSportsFixtureSingleLineupTeamDto(1, ApiSportsFixtureSingleLineupColorsDto(player = ApiSportsFixtureSingleLineupColorDto(primary = "red"))),
                coach = ApiSportsFixtureSingleLineupCoachDto(id = 0, name = " "),
            )

        val issues = inspector.inspect(target(lineups = listOf(lineup, validLineup(2))))

        assertThat(issues.map { it.inspectionRuleCode.value }).containsExactly("COACH_ID_MISSING", "COACH_NAME_MISSING", "LINEUP_COLOR_INVALID")
        assertThat(issues.map { it.suggestedType }).containsExactly(QualityIssueType.OUT_OF_RANGE, QualityIssueType.UNEXPECTED_BLANK, QualityIssueType.INVALID_FORMAT)
    }

    private fun target(status: String? = "NS", lineups: List<ApiSportsFixtureSingleLineupDto?>?): ApiSportsFixtureSingleRootDto =
        ApiSportsFixtureSingleRootDto(
            response =
                listOf(
                    ApiSportsFixtureSingleResponseDto(
                        fixture = ApiSportsFixtureSingleFixtureDto(status = ApiSportsFixtureSingleStatusDto(short = status)),
                        teams = ApiSportsFixtureSingleTeamsDto(home = ApiSportsFixtureSingleTeamDto(id = 1), away = ApiSportsFixtureSingleTeamDto(id = 2)),
                        lineups = lineups,
                    ),
                ),
        )

    private fun validLineup(teamId: Long): ApiSportsFixtureSingleLineupDto =
        ApiSportsFixtureSingleLineupDto(
            team = ApiSportsFixtureSingleLineupTeamDto(teamId),
            formation = "4-3-3",
            startXI = validStartingPlayers().map(::entry),
            substitutes = emptyList(),
        )

    private fun validStartingPlayers(): List<ApiSportsFixtureSingleLineupPlayerDto> =
        (1L..11L).map { id -> player(id, "Player $id", id, if (id == 1L) "G" else "D", "${id}:1") }

    private fun player(id: Long?, name: String?, number: Long?, pos: String?, grid: String?): ApiSportsFixtureSingleLineupPlayerDto =
        ApiSportsFixtureSingleLineupPlayerDto(id, name, number, pos, grid)

    private fun entry(player: ApiSportsFixtureSingleLineupPlayerDto): ApiSportsFixtureSingleLineupPlayerEntryDto = ApiSportsFixtureSingleLineupPlayerEntryDto(player)
}
