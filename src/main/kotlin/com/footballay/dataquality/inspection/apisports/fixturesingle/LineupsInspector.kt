package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.footballay.dataquality.inspection.FoundIssue
import com.footballay.dataquality.inspection.InspectionEvidence
import com.footballay.dataquality.inspection.InspectionName
import com.footballay.dataquality.inspection.InspectionRuleCode
import com.footballay.dataquality.inspection.InspectionSeverity
import com.footballay.dataquality.inspection.QualityInspector
import com.footballay.dataquality.inspection.QualityIssueType

/** API Sports Fixture Single의 팀별 선발 및 교체 명단 무결성을 검사한다. */
class LineupsInspector : QualityInspector<ApiSportsFixtureSingleRootDto> {
    override val inspectionName = InspectionName.of("LINEUPS")

    override fun inspect(target: ApiSportsFixtureSingleRootDto): List<FoundIssue> {
        val findings = mutableListOf<FoundIssue>()

        target.response.orEmpty().forEachIndexed responseLoop@{ responseIndex, response ->
            if (response == null) return@responseLoop

            val lineups = response.lineups.orEmpty()
            if (lineups.isEmpty()) return@responseLoop

            val basePath = "$.response[$responseIndex].lineups"
            val statusShort = response.fixture?.status?.short
            val fixtureTeamIds = response.fixtureTeamIds()
            inspectTeamStructure(findings, lineups, basePath, responseIndex, statusShort, fixtureTeamIds)

            lineups.forEachIndexed lineupLoop@{ lineupIndex, lineup ->
                val path = "$basePath[$lineupIndex]"
                if (lineup == null) {
                    findings += issue(Code.LINEUP_OBJECT_NULL, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "Lineup object is null", path, evidence(responseIndex, lineupIndex))
                    return@lineupLoop
                }
                inspectLineupTeam(findings, lineup, path, responseIndex, lineupIndex)
                inspectFormation(findings, lineup, path, responseIndex, lineupIndex)
                inspectStartingXi(findings, lineup, path, responseIndex, lineupIndex, statusShort)
                inspectSubstitutes(findings, lineup, path, responseIndex, lineupIndex)
                inspectCoach(findings, lineup, path, responseIndex, lineupIndex)
                inspectColors(findings, lineup, path, responseIndex, lineupIndex)
                inspectSquadNumbers(findings, lineup, path, responseIndex, lineupIndex)
            }
        }

        return findings
    }

    private fun inspectTeamStructure(
        findings: MutableList<FoundIssue>,
        lineups: List<ApiSportsFixtureSingleLineupDto?>,
        basePath: String,
        responseIndex: Int,
        statusShort: String?,
        fixtureTeamIds: Set<Long>,
    ) {
        if (lineups.size != 2) {
            findings += issue(Code.LINEUP_TEAM_COUNT_INVALID, QualityIssueType.INCONSISTENT_INFORMATION, severityForAvailability(statusShort), "Lineup team count is not two", basePath, fixtureSingleEvidence("responseIndex" to responseIndex.toString(), "actualCount" to lineups.size.toString()))
        }

        val lineupTeams = lineups.mapIndexedNotNull { lineupIndex, lineup -> lineup?.team?.id?.takeIf { it > 0 }?.let { lineupIndex to it } }
        val lineupTeamIds = lineupTeams.map { it.second }
        lineupTeams.forEach { (lineupIndex, teamId) ->
            if (fixtureTeamIds.isNotEmpty() && teamId !in fixtureTeamIds) {
                findings += issue(Code.LINEUP_UNKNOWN_TEAM, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.ERROR, "Lineup team is not a fixture team", "$basePath[$lineupIndex].team.id", evidence(responseIndex, lineupIndex, "teamId" to teamId.toString()))
            }
        }
        lineupTeamIds.groupingBy { it }.eachCount().filterValues { it > 1 }.forEach { (teamId, _) ->
            findings += issue(Code.LINEUP_DUPLICATE_TEAM, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.ERROR, "Lineup team is duplicated", basePath, fixtureSingleEvidence("responseIndex" to responseIndex.toString(), "teamId" to teamId.toString()))
        }
        if (fixtureTeamIds.size == 2 && lineupTeamIds.any { it in fixtureTeamIds } && fixtureTeamIds.any { it !in lineupTeamIds }) {
            findings += issue(Code.LINEUP_MISSING_FIXTURE_TEAM, QualityIssueType.INCONSISTENT_INFORMATION, severityForAvailability(statusShort), "Lineup is missing a fixture team", basePath, fixtureSingleEvidence("responseIndex" to responseIndex.toString(), "fixtureTeamIds" to fixtureTeamIds.sorted().joinToString(","), "lineupTeamIds" to lineupTeamIds.joinToString(",")))
        }
    }

    private fun inspectLineupTeam(
        findings: MutableList<FoundIssue>,
        lineup: ApiSportsFixtureSingleLineupDto,
        path: String,
        responseIndex: Int,
        lineupIndex: Int,
    ) {
        val teamId = lineup.team?.id
        when {
            teamId == null ->
                findings += issue(Code.LINEUP_TEAM_ID_MISSING_OR_INVALID, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "Lineup team ID is missing", "$path.team.id", evidence(responseIndex, lineupIndex))
            teamId <= 0 ->
                findings += issue(Code.LINEUP_TEAM_ID_MISSING_OR_INVALID, QualityIssueType.OUT_OF_RANGE, InspectionSeverity.ERROR, "Lineup team ID is invalid", "$path.team.id", evidence(responseIndex, lineupIndex, "teamId" to teamId.toString()))
        }
    }

    private fun inspectFormation(findings: MutableList<FoundIssue>, lineup: ApiSportsFixtureSingleLineupDto, path: String, responseIndex: Int, lineupIndex: Int) {
        if (lineup.startXI.isNullOrEmpty()) return
        when {
            lineup.formation == null -> findings += issue(Code.LINEUP_FORMATION_MISSING, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.WARN, "Formation is missing", "$path.formation", evidence(responseIndex, lineupIndex))
            lineup.formation.isBlank() -> findings += issue(Code.LINEUP_FORMATION_MISSING, QualityIssueType.UNEXPECTED_BLANK, InspectionSeverity.WARN, "Formation is blank", "$path.formation", evidence(responseIndex, lineupIndex))
            !formationPattern.matches(lineup.formation) -> findings += issue(Code.LINEUP_FORMATION_INVALID_FORMAT, QualityIssueType.INVALID_FORMAT, InspectionSeverity.WARN, "Formation has an invalid format", "$path.formation", evidence(responseIndex, lineupIndex, "actualValue" to lineup.formation))
        }
    }

    private fun inspectStartingXi(findings: MutableList<FoundIssue>, lineup: ApiSportsFixtureSingleLineupDto, path: String, responseIndex: Int, lineupIndex: Int, statusShort: String?) {
        val startXi = lineup.startXI.orEmpty()
        if (startXi.isEmpty()) {
            val suggestedType = if (lineup.startXI == null) QualityIssueType.UNEXPECTED_NULL else QualityIssueType.INCONSISTENT_INFORMATION
            findings += issue(Code.STARTING_XI_MISSING_OR_EMPTY, suggestedType, severityForAvailability(statusShort), "Starting XI is missing or empty", "$path.startXI", evidence(responseIndex, lineupIndex))
            return
        }
        if (startXi.size != 11) findings += issue(Code.STARTING_XI_COUNT_INVALID, QualityIssueType.INCONSISTENT_INFORMATION, severityForAvailability(statusShort), "Starting XI count is not eleven", "$path.startXI", evidence(responseIndex, lineupIndex, "actualCount" to startXi.size.toString()))

        val validIds = mutableListOf<Long>()
        val grids = mutableListOf<String>()
        var canInspectGoalkeeperCount = startXi.size == 11
        startXi.forEachIndexed { playerIndex, entry ->
            val playerPath = "$path.startXI[$playerIndex].player"
            if (entry == null) {
                findings += issue(Code.STARTING_PLAYER_OBJECT_NULL, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "Starting player entry is null", "$path.startXI[$playerIndex]", evidence(responseIndex, lineupIndex, "playerIndex" to playerIndex.toString(), "lineupType" to "STARTING"))
                canInspectGoalkeeperCount = false
                return@forEachIndexed
            }
            val player = entry.player
            if (player == null) {
                findings += issue(Code.STARTING_PLAYER_OBJECT_NULL, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "Starting player object is null", playerPath, evidence(responseIndex, lineupIndex, "playerIndex" to playerIndex.toString(), "lineupType" to "STARTING"))
                canInspectGoalkeeperCount = false
                return@forEachIndexed
            }
            inspectStartingPlayer(findings, player, playerPath, responseIndex, lineupIndex, playerIndex)
            player.id?.takeIf { it > 0 }?.let(validIds::add)
            player.grid?.takeIf { gridPattern.matches(it) }?.let(grids::add)
            if (player.pos !in positions) canInspectGoalkeeperCount = false
        }
        reportDuplicates(findings, validIds, Code.STARTING_PLAYER_DUPLICATED, "Starting player is duplicated", "$path.startXI", responseIndex, lineupIndex, "playerId")
        reportDuplicates(findings, grids, Code.STARTING_PLAYER_GRID_DUPLICATED, "Starting player grid is duplicated", "$path.startXI", responseIndex, lineupIndex, "grid")
        if (canInspectGoalkeeperCount) {
            val goalkeeperCount = startXi.count { it!!.player!!.pos == "G" }
            if (goalkeeperCount != 1) findings += issue(Code.STARTING_GOALKEEPER_COUNT_INVALID, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.ERROR, "Starting XI goalkeeper count is not one", "$path.startXI", evidence(responseIndex, lineupIndex, "actualCount" to goalkeeperCount.toString()))
        }
    }

    private fun inspectStartingPlayer(findings: MutableList<FoundIssue>, player: ApiSportsFixtureSingleLineupPlayerDto, path: String, responseIndex: Int, lineupIndex: Int, playerIndex: Int) {
        val playerEvidence = evidence(responseIndex, lineupIndex, "playerIndex" to playerIndex.toString(), "lineupType" to "STARTING")
        when {
            player.id == null -> findings += issue(Code.STARTING_PLAYER_ID_MISSING, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "Starting player ID is missing", "$path.id", playerEvidence)
            player.id <= 0 -> findings += issue(Code.STARTING_PLAYER_ID_MISSING, QualityIssueType.OUT_OF_RANGE, InspectionSeverity.ERROR, "Starting player ID is invalid", "$path.id", playerEvidence)
        }
        when {
            player.name == null -> findings += issue(Code.STARTING_PLAYER_NAME_MISSING, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "Starting player name is missing", "$path.name", playerEvidence)
            player.name.isBlank() -> findings += issue(Code.STARTING_PLAYER_NAME_MISSING, QualityIssueType.UNEXPECTED_BLANK, InspectionSeverity.ERROR, "Starting player name is blank", "$path.name", playerEvidence)
        }
        when {
            player.number == null -> findings += issue(Code.STARTING_PLAYER_NUMBER_MISSING, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.WARN, "Starting player number is missing", "$path.number", playerEvidence)
            player.number <= 0 -> findings += issue(Code.STARTING_PLAYER_NUMBER_MISSING, QualityIssueType.OUT_OF_RANGE, InspectionSeverity.WARN, "Starting player number is invalid", "$path.number", playerEvidence)
        }
        when {
            player.pos == null -> findings += issue(Code.STARTING_PLAYER_POSITION_MISSING, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "Starting player position is missing", "$path.pos", playerEvidence)
            player.pos.isBlank() -> findings += issue(Code.STARTING_PLAYER_POSITION_MISSING, QualityIssueType.UNEXPECTED_BLANK, InspectionSeverity.ERROR, "Starting player position is blank", "$path.pos", playerEvidence)
            player.pos !in positions -> findings += issue(Code.STARTING_PLAYER_POSITION_UNKNOWN, QualityIssueType.UNEXPECTED_RESPONSE, InspectionSeverity.WARN, "Starting player position is unknown", "$path.pos", playerEvidence)
        }
        when {
            player.grid == null -> findings += issue(Code.STARTING_PLAYER_GRID_MISSING, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "Starting player grid is missing", "$path.grid", playerEvidence)
            player.grid.isBlank() -> findings += issue(Code.STARTING_PLAYER_GRID_MISSING, QualityIssueType.UNEXPECTED_BLANK, InspectionSeverity.ERROR, "Starting player grid is blank", "$path.grid", playerEvidence)
            !gridPattern.matches(player.grid) -> findings += issue(Code.STARTING_PLAYER_GRID_INVALID_FORMAT, QualityIssueType.INVALID_FORMAT, InspectionSeverity.ERROR, "Starting player grid has an invalid format", "$path.grid", playerEvidence)
        }
    }

    private fun inspectSubstitutes(findings: MutableList<FoundIssue>, lineup: ApiSportsFixtureSingleLineupDto, path: String, responseIndex: Int, lineupIndex: Int) {
        val substituteIds = mutableListOf<Long>()
        lineup.substitutes.orEmpty().forEachIndexed { playerIndex, entry ->
            val playerPath = "$path.substitutes[$playerIndex].player"
            val playerEvidence = evidence(responseIndex, lineupIndex, "playerIndex" to playerIndex.toString(), "lineupType" to "SUBSTITUTE")
            if (entry == null) {
                findings += issue(Code.SUBSTITUTE_PLAYER_OBJECT_NULL, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "Substitute player entry is null", "$path.substitutes[$playerIndex]", playerEvidence)
                return@forEachIndexed
            }
            val player = entry.player
            if (player == null) {
                findings += issue(Code.SUBSTITUTE_PLAYER_OBJECT_NULL, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "Substitute player object is null", playerPath, playerEvidence)
                return@forEachIndexed
            }
            when {
                player.id == null -> findings += issue(Code.SUBSTITUTE_PLAYER_ID_MISSING, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "Substitute player ID is missing", "$playerPath.id", playerEvidence)
                player.id <= 0 -> findings += issue(Code.SUBSTITUTE_PLAYER_ID_MISSING, QualityIssueType.OUT_OF_RANGE, InspectionSeverity.ERROR, "Substitute player ID is invalid", "$playerPath.id", playerEvidence)
                else -> substituteIds += player.id
            }
            when {
                player.name == null -> findings += issue(Code.SUBSTITUTE_PLAYER_NAME_MISSING, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "Substitute player name is missing", "$playerPath.name", playerEvidence)
                player.name.isBlank() -> findings += issue(Code.SUBSTITUTE_PLAYER_NAME_MISSING, QualityIssueType.UNEXPECTED_BLANK, InspectionSeverity.ERROR, "Substitute player name is blank", "$playerPath.name", playerEvidence)
            }
            when {
                player.number == null -> findings += issue(Code.SUBSTITUTE_PLAYER_NUMBER_MISSING, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.WARN, "Substitute player number is missing", "$playerPath.number", playerEvidence)
                player.number <= 0 -> findings += issue(Code.SUBSTITUTE_PLAYER_NUMBER_MISSING, QualityIssueType.OUT_OF_RANGE, InspectionSeverity.WARN, "Substitute player number is invalid", "$playerPath.number", playerEvidence)
            }
            when {
                player.pos == null -> findings += issue(Code.SUBSTITUTE_PLAYER_POSITION_MISSING, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "Substitute player position is missing", "$playerPath.pos", playerEvidence)
                player.pos.isBlank() -> findings += issue(Code.SUBSTITUTE_PLAYER_POSITION_MISSING, QualityIssueType.UNEXPECTED_BLANK, InspectionSeverity.ERROR, "Substitute player position is blank", "$playerPath.pos", playerEvidence)
                player.pos !in positions -> findings += issue(Code.SUBSTITUTE_PLAYER_POSITION_UNKNOWN, QualityIssueType.UNEXPECTED_RESPONSE, InspectionSeverity.WARN, "Substitute player position is unknown", "$playerPath.pos", playerEvidence)
            }
            if (player.grid != null) findings += issue(Code.SUBSTITUTE_GRID_PRESENT, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.WARN, "Substitute player grid is present", "$playerPath.grid", playerEvidence)
        }
        reportDuplicates(findings, substituteIds, Code.SUBSTITUTE_PLAYER_DUPLICATED, "Substitute player is duplicated", "$path.substitutes", responseIndex, lineupIndex, "playerId")

        val startingIds = lineup.startXI.orEmpty().mapNotNull { it?.player?.id?.takeIf { id -> id > 0 } }.toSet()
        substituteIds.filter { it in startingIds }.distinct().forEach { playerId ->
            findings += issue(Code.PLAYER_IN_START_AND_SUBSTITUTES, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.ERROR, "Player is in both starting XI and substitutes", path, evidence(responseIndex, lineupIndex, "playerId" to playerId.toString()))
        }
    }

    private fun inspectCoach(findings: MutableList<FoundIssue>, lineup: ApiSportsFixtureSingleLineupDto, path: String, responseIndex: Int, lineupIndex: Int) {
        val coach = lineup.coach ?: return
        when {
            coach.id == null -> findings += issue(Code.COACH_ID_MISSING, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "Coach ID is missing", "$path.coach.id", evidence(responseIndex, lineupIndex))
            coach.id <= 0 -> findings += issue(Code.COACH_ID_MISSING, QualityIssueType.OUT_OF_RANGE, InspectionSeverity.ERROR, "Coach ID is invalid", "$path.coach.id", evidence(responseIndex, lineupIndex, "coachId" to coach.id.toString()))
        }
        when {
            coach.name == null -> findings += issue(Code.COACH_NAME_MISSING, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.WARN, "Coach name is missing", "$path.coach.name", evidence(responseIndex, lineupIndex))
            coach.name.isBlank() -> findings += issue(Code.COACH_NAME_MISSING, QualityIssueType.UNEXPECTED_BLANK, InspectionSeverity.WARN, "Coach name is blank", "$path.coach.name", evidence(responseIndex, lineupIndex))
        }
    }

    private fun inspectColors(findings: MutableList<FoundIssue>, lineup: ApiSportsFixtureSingleLineupDto, path: String, responseIndex: Int, lineupIndex: Int) {
        val colors = lineup.team?.colors ?: return
        inspectColor(findings, colors.player, "$path.team.colors.player", responseIndex, lineupIndex, "PLAYER")
        inspectColor(findings, colors.goalkeeper, "$path.team.colors.goalkeeper", responseIndex, lineupIndex, "GOALKEEPER")
    }

    private fun inspectColor(findings: MutableList<FoundIssue>, color: ApiSportsFixtureSingleLineupColorDto?, path: String, responseIndex: Int, lineupIndex: Int, colorType: String) {
        color ?: return
        listOf("primary" to color.primary, "number" to color.number, "border" to color.border).forEach { (field, value) ->
            if (value != null && !colorPattern.matches(value)) findings += issue(Code.LINEUP_COLOR_INVALID, QualityIssueType.INVALID_FORMAT, InspectionSeverity.WARN, "Lineup color is invalid", "$path.$field", evidence(responseIndex, lineupIndex, "colorType" to colorType, "actualValue" to value))
        }
    }

    private fun inspectSquadNumbers(findings: MutableList<FoundIssue>, lineup: ApiSportsFixtureSingleLineupDto, path: String, responseIndex: Int, lineupIndex: Int) {
        val numbers = (lineup.startXI.orEmpty() + lineup.substitutes.orEmpty()).mapNotNull { it?.player?.number?.takeIf { number -> number > 0 } }
        reportDuplicates(findings, numbers, Code.DUPLICATE_SQUAD_NUMBER, "Squad number is duplicated", path, responseIndex, lineupIndex, "number")
    }

    private fun <T> reportDuplicates(findings: MutableList<FoundIssue>, values: List<T>, code: Code, title: String, path: String, responseIndex: Int, lineupIndex: Int, evidenceKey: String) {
        values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { value ->
            findings += issue(code, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.ERROR, title, path, evidence(responseIndex, lineupIndex, evidenceKey to value.toString()))
        }
    }

    private fun ApiSportsFixtureSingleResponseDto.fixtureTeamIds(): Set<Long> {
        val home = teams?.home?.id?.takeIf { it > 0 }
        val away = teams?.away?.id?.takeIf { it > 0 }
        return if (home != null && away != null && home != away) setOf(home, away) else emptySet()
    }

    private fun severityForAvailability(statusShort: String?): InspectionSeverity = if (FixtureStatusSemantics.hasStarted(statusShort)) InspectionSeverity.ERROR else InspectionSeverity.WARN

    private fun issue(code: Code, suggestedType: QualityIssueType, severity: InspectionSeverity, title: String, path: String, evidence: InspectionEvidence): FoundIssue =
        fixtureSingleFoundIssue(suggestedType, inspectionName, code, severity, title, "lineups", path, evidence)

    private fun evidence(responseIndex: Int, lineupIndex: Int, vararg extra: Pair<String, String?>): InspectionEvidence =
        fixtureSingleEvidence("responseIndex" to responseIndex.toString(), "lineupIndex" to lineupIndex.toString(), *extra)

    private enum class Code : InspectionRuleCode {
        LINEUP_OBJECT_NULL, LINEUP_TEAM_ID_MISSING_OR_INVALID, LINEUP_TEAM_COUNT_INVALID, LINEUP_UNKNOWN_TEAM, LINEUP_DUPLICATE_TEAM, LINEUP_MISSING_FIXTURE_TEAM, LINEUP_FORMATION_MISSING, LINEUP_FORMATION_INVALID_FORMAT,
        STARTING_XI_MISSING_OR_EMPTY, STARTING_XI_COUNT_INVALID, STARTING_PLAYER_OBJECT_NULL, STARTING_PLAYER_ID_MISSING, STARTING_PLAYER_NAME_MISSING, STARTING_PLAYER_NUMBER_MISSING, STARTING_PLAYER_POSITION_MISSING, STARTING_PLAYER_POSITION_UNKNOWN, STARTING_PLAYER_GRID_MISSING, STARTING_PLAYER_GRID_INVALID_FORMAT, STARTING_PLAYER_GRID_DUPLICATED, STARTING_GOALKEEPER_COUNT_INVALID, STARTING_PLAYER_DUPLICATED,
        SUBSTITUTE_PLAYER_OBJECT_NULL, SUBSTITUTE_PLAYER_ID_MISSING, SUBSTITUTE_PLAYER_NAME_MISSING, SUBSTITUTE_PLAYER_NUMBER_MISSING, SUBSTITUTE_PLAYER_POSITION_MISSING, SUBSTITUTE_PLAYER_POSITION_UNKNOWN, SUBSTITUTE_PLAYER_DUPLICATED, PLAYER_IN_START_AND_SUBSTITUTES, SUBSTITUTE_GRID_PRESENT,
        DUPLICATE_SQUAD_NUMBER, COACH_ID_MISSING, COACH_NAME_MISSING, LINEUP_COLOR_INVALID,
        ;

        override val value: String get() = name
    }

    private companion object {
        val formationPattern = Regex("\\d+(?:-\\d+)+")
        val gridPattern = Regex("[1-9]\\d*:[1-9]\\d*")
        val colorPattern = Regex("[0-9a-fA-F]{6}")
        val positions = setOf("G", "D", "M", "F")
    }
}
