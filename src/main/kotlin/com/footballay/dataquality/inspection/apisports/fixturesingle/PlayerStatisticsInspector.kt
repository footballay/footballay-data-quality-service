package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.footballay.dataquality.inspection.FoundIssue
import com.footballay.dataquality.inspection.InspectionEvidence
import com.footballay.dataquality.inspection.InspectionName
import com.footballay.dataquality.inspection.InspectionRuleCode
import com.footballay.dataquality.inspection.InspectionSeverity
import com.footballay.dataquality.inspection.QualityInspector
import com.footballay.dataquality.inspection.QualityIssueType

/** API Sports Fixture Single의 선수 통계 구조와 선수별 수치 관계를 검사한다. */
class PlayerStatisticsInspector : QualityInspector<ApiSportsFixtureSingleRootDto> {
    override val inspectionName = InspectionName.of("PLAYER_STATISTICS")

    override fun inspect(target: ApiSportsFixtureSingleRootDto): List<FoundIssue> {
        val findings = mutableListOf<FoundIssue>()
        target.response.orEmpty().forEachIndexed responseLoop@{ responseIndex, response ->
            if (response == null) return@responseLoop
            val groups = response.players.orEmpty()
            if (groups.isEmpty()) return@responseLoop
            val basePath = "$.response[$responseIndex].players"
            val fixtureTeamIds = response.fixtureTeamIds()
            val validTeamIds = mutableListOf<Long>()
            val structureSeverity = severityForAvailability(response.fixture?.status?.short)

            if (groups.size != 2) findings += issue(Code.PLAYER_STATISTICS_TEAM_COUNT_INVALID, QualityIssueType.INCONSISTENT_INFORMATION, structureSeverity, "Player statistics team group count is not two", basePath, evidence(responseIndex, "actualCount" to groups.size.toString()))
            groups.forEachIndexed groupLoop@{ groupIndex, group ->
                if (group == null) {
                    findings += issue(Code.PLAYER_STATISTICS_TEAM_GROUP_OBJECT_NULL, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "Player statistics team group is null", "$basePath[$groupIndex]", evidence(responseIndex, "groupIndex" to groupIndex.toString()))
                    return@groupLoop
                }
                val teamId = group.team?.id?.takeIf { it > 0 }
                if (teamId != null) {
                    validTeamIds += teamId
                    if (fixtureTeamIds.isNotEmpty() && teamId !in fixtureTeamIds) findings += issue(Code.PLAYER_STATISTICS_UNKNOWN_TEAM, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.ERROR, "Player statistics team is not a fixture team", "$basePath[$groupIndex].team.id", evidence(responseIndex, groupIndex, "teamId" to teamId.toString()))
                }
                inspectGroup(findings, responseIndex, groupIndex, group, "$basePath[$groupIndex]")
            }
            validTeamIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { teamId ->
                findings += issue(Code.PLAYER_STATISTICS_DUPLICATE_TEAM, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.ERROR, "Player statistics team is duplicated", basePath, evidence(responseIndex, "teamId" to teamId.toString()))
            }
            val missingFixtureTeamIds = fixtureTeamIds - validTeamIds.toSet()
            if (fixtureTeamIds.size == 2 && missingFixtureTeamIds.isNotEmpty()) findings += issue(Code.PLAYER_STATISTICS_MISSING_FIXTURE_TEAM, QualityIssueType.INCONSISTENT_INFORMATION, structureSeverity, "Player statistics are missing a fixture team", basePath, evidence(responseIndex, "missingTeamIds" to missingFixtureTeamIds.sorted().joinToString(",")))
        }
        return findings
    }

    private fun inspectGroup(findings: MutableList<FoundIssue>, responseIndex: Int, groupIndex: Int, group: ApiSportsFixtureSinglePlayerStatisticsTeamDto, path: String) {
        val playerIds = mutableListOf<Long>()
        val captains = mutableListOf<Int>()
        group.players.orEmpty().forEachIndexed playerLoop@{ playerIndex, playerStatistics ->
            if (playerStatistics == null) {
                findings += issue(Code.PLAYER_STATISTICS_PLAYER_OBJECT_NULL, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "$path players entry is null", "$path.players[$playerIndex]", evidence(responseIndex, groupIndex, "playerIndex" to playerIndex.toString()))
                return@playerLoop
            }
            val playerPath = "$path.players[$playerIndex]"
            val player = playerStatistics.player
            val playerEvidence = evidence(responseIndex, groupIndex, "playerIndex" to playerIndex.toString())
            if (player == null) {
                findings += issue(Code.PLAYER_STATISTICS_PLAYER_REFERENCE_OBJECT_NULL, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "Player reference is null", "$playerPath.player", playerEvidence)
            } else {
                when {
                    player.id == null -> findings += issue(Code.PLAYER_ID_MISSING, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "Player ID is missing", "$playerPath.player.id", playerEvidence)
                    player.id <= 0 -> findings += issue(Code.PLAYER_ID_MISSING, QualityIssueType.OUT_OF_RANGE, InspectionSeverity.ERROR, "Player ID is invalid", "$playerPath.player.id", playerEvidence)
                    else -> playerIds += player.id
                }
                when {
                    player.name == null -> findings += issue(Code.PLAYER_NAME_MISSING, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "Player name is missing", "$playerPath.player.name", playerEvidence)
                    player.name.isBlank() -> findings += issue(Code.PLAYER_NAME_MISSING, QualityIssueType.UNEXPECTED_BLANK, InspectionSeverity.ERROR, "Player name is blank", "$playerPath.player.name", playerEvidence)
                }
            }
            val entries = playerStatistics.statistics
            if (entries == null) {
                findings += issue(Code.PLAYER_STATISTICS_LIST_NULL, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.WARN, "Player statistics list is null", "$playerPath.statistics", playerEvidence)
                return@playerLoop
            }
            if (entries.isEmpty()) findings += issue(Code.PLAYER_STATISTICS_LIST_EMPTY, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.WARN, "Player statistics list is empty", "$playerPath.statistics", playerEvidence)
            if (entries.size >= 2) findings += issue(Code.PLAYER_STATISTICS_ENTRY_COUNT_UNEXPECTED, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.WARN, "Player has multiple statistics entries", "$playerPath.statistics", evidence(responseIndex, groupIndex, "playerIndex" to playerIndex.toString(), "actualCount" to entries.size.toString()))
            entries.forEachIndexed entryLoop@{ entryIndex, entry ->
                if (entry == null) {
                    findings += issue(Code.PLAYER_STATISTICS_ENTRY_OBJECT_NULL, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "$playerPath statistics entry is null", "$playerPath.statistics[$entryIndex]", playerEvidence)
                    return@entryLoop
                }
                val entryPath = "$playerPath.statistics[$entryIndex]"
                inspectEntry(findings, responseIndex, groupIndex, playerIndex, entry, entryPath)?.let { if (it) captains += playerIndex }
            }
        }
        playerIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { playerId -> findings += issue(Code.PLAYER_DUPLICATED_IN_TEAM, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.ERROR, "Player is duplicated in a team group", "$path.players", evidence(responseIndex, groupIndex, "playerId" to playerId.toString())) }
        if (captains.distinct().size > 1) findings += issue(Code.MULTIPLE_CAPTAINS_IN_TEAM, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.ERROR, "Multiple captains are marked in a team group", "$path.players", evidence(responseIndex, groupIndex, "captainPlayerIndexes" to captains.distinct().joinToString(",")))
    }

    private fun inspectEntry(findings: MutableList<FoundIssue>, responseIndex: Int, groupIndex: Int, playerIndex: Int, entry: ApiSportsFixtureSinglePlayerStatisticEntryDto, path: String): Boolean? {
        val games = entry.games
        fun entryEvidence(vararg values: Pair<String, String?>): InspectionEvidence = evidence(responseIndex, groupIndex, "playerIndex" to playerIndex.toString(), *values)
        if (games != null) {
            if (games.minutes != null && (games.minutes < 0 || games.minutes > MAX_PLAYER_MINUTES)) findings += issue(Code.PLAYER_MINUTES_INVALID, QualityIssueType.OUT_OF_RANGE, InspectionSeverity.ERROR, "Player minutes are outside the supported range", "$path.games.minutes", entryEvidence("actualValue" to games.minutes.toString()))
            if (games.number != null && (games.number <= 0 || games.number > MAX_PLAYER_NUMBER)) findings += issue(Code.PLAYER_NUMBER_INVALID, QualityIssueType.OUT_OF_RANGE, InspectionSeverity.WARN, "Player number is outside the supported range", "$path.games.number", entryEvidence("actualValue" to games.number.toString()))
            if (games.position != null && games.position !in positions) findings += issue(Code.PLAYER_POSITION_UNKNOWN, QualityIssueType.UNEXPECTED_RESPONSE, InspectionSeverity.WARN, "Player position is unknown", "$path.games.position", entryEvidence("actualValue" to games.position))
            inspectRating(findings, games.rating, "$path.games.rating", entryEvidence())
        }
        countValues(entry).filterValues { it != null && it < 0 }.forEach { (field, value) -> findings += issue(Code.NEGATIVE_PLAYER_STAT_VALUE, QualityIssueType.OUT_OF_RANGE, InspectionSeverity.ERROR, "Player statistic value is negative", "$path.$field", entryEvidence("actualValue" to value.toString())) }
        compareNotExceeding(findings, entry.shots?.on, entry.shots?.total, Code.SHOTS_ON_EXCEED_TOTAL, "$path.shots", responseIndex, groupIndex, playerIndex, "shotsOn", "shotsTotal")
        compareNotExceeding(findings, entry.passes?.accuracy, entry.passes?.total, Code.PASSES_ACCURATE_EXCEED_TOTAL, "$path.passes", responseIndex, groupIndex, playerIndex, "passesAccurate", "passesTotal")
        compareNotExceeding(findings, entry.duels?.won, entry.duels?.total, Code.DUELS_WON_EXCEED_TOTAL, "$path.duels", responseIndex, groupIndex, playerIndex, "duelsWon", "duelsTotal")
        compareNotExceeding(findings, entry.dribbles?.success, entry.dribbles?.attempts, Code.DRIBBLES_SUCCESS_EXCEED_ATTEMPTS, "$path.dribbles", responseIndex, groupIndex, playerIndex, "dribblesSuccess", "dribblesAttempts")
        if (games?.position in outfieldPositions && entry.goals?.saves != null && entry.goals.saves > 0) findings += issue(Code.OUTFIELD_PLAYER_GOALKEEPER_STAT_INVALID, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.ERROR, "Outfield player has goalkeeper saves", "$path.goals.saves", entryEvidence("actualValue" to entry.goals.saves.toString()))
        return games?.captain
    }

    private fun inspectRating(findings: MutableList<FoundIssue>, rating: String?, path: String, evidence: InspectionEvidence) {
        if (rating == null) return
        val numeric = rating.toDoubleOrNull()
        if (numeric == null || !numeric.isFinite()) findings += issue(Code.PLAYER_RATING_INVALID, QualityIssueType.INVALID_FORMAT, InspectionSeverity.WARN, "Player rating has an invalid format", path, evidence)
        else if (numeric !in 0.0..10.0) findings += issue(Code.PLAYER_RATING_INVALID, QualityIssueType.OUT_OF_RANGE, InspectionSeverity.WARN, "Player rating is outside the supported range", path, evidence)
    }

    private fun compareNotExceeding(findings: MutableList<FoundIssue>, component: Long?, total: Long?, code: Code, path: String, responseIndex: Int, groupIndex: Int, playerIndex: Int, componentKey: String, totalKey: String) {
        if (component != null && total != null && component >= 0 && total >= 0 && component > total) findings += issue(code, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.ERROR, "Player statistic component exceeds total", path, evidence(responseIndex, groupIndex, "playerIndex" to playerIndex.toString(), componentKey to component.toString(), totalKey to total.toString()))
    }

    private fun countValues(entry: ApiSportsFixtureSinglePlayerStatisticEntryDto): Map<String, Long?> = mapOf(
        "offsides" to entry.offsides, "shots.total" to entry.shots?.total, "shots.on" to entry.shots?.on, "goals.total" to entry.goals?.total, "goals.conceded" to entry.goals?.conceded, "goals.assists" to entry.goals?.assists, "goals.saves" to entry.goals?.saves, "passes.total" to entry.passes?.total, "passes.key" to entry.passes?.key, "passes.accuracy" to entry.passes?.accuracy, "tackles.total" to entry.tackles?.total, "tackles.blocks" to entry.tackles?.blocks, "tackles.interceptions" to entry.tackles?.interceptions, "duels.total" to entry.duels?.total, "duels.won" to entry.duels?.won, "dribbles.attempts" to entry.dribbles?.attempts, "dribbles.success" to entry.dribbles?.success, "dribbles.past" to entry.dribbles?.past, "fouls.drawn" to entry.fouls?.drawn, "fouls.committed" to entry.fouls?.committed, "cards.yellow" to entry.cards?.yellow, "cards.red" to entry.cards?.red, "penalty.won" to entry.penalty?.won, "penalty.committed" to entry.penalty?.committed, "penalty.scored" to entry.penalty?.scored, "penalty.missed" to entry.penalty?.missed, "penalty.saved" to entry.penalty?.saved,
    )

    private fun ApiSportsFixtureSingleResponseDto.fixtureTeamIds(): Set<Long> {
        val home = teams?.home?.id?.takeIf { it > 0 }
        val away = teams?.away?.id?.takeIf { it > 0 }
        return if (home != null && away != null && home != away) setOf(home, away) else emptySet()
    }

    private fun severityForAvailability(statusShort: String?): InspectionSeverity = if (FixtureStatusSemantics.hasStarted(statusShort)) InspectionSeverity.ERROR else InspectionSeverity.WARN
    private fun issue(code: Code, suggestedType: QualityIssueType, severity: InspectionSeverity, title: String, path: String, evidence: InspectionEvidence): FoundIssue = fixtureSingleFoundIssue(suggestedType, inspectionName, code, severity, title, "players", path, evidence)
    private fun evidence(responseIndex: Int, vararg values: Pair<String, String?>): InspectionEvidence = fixtureSingleEvidence("responseIndex" to responseIndex.toString(), *values)
    private fun evidence(responseIndex: Int, groupIndex: Int, vararg values: Pair<String, String?>): InspectionEvidence = fixtureSingleEvidence("responseIndex" to responseIndex.toString(), "groupIndex" to groupIndex.toString(), *values)

    private enum class Code : InspectionRuleCode {
        PLAYER_STATISTICS_TEAM_COUNT_INVALID, PLAYER_STATISTICS_TEAM_GROUP_OBJECT_NULL, PLAYER_STATISTICS_UNKNOWN_TEAM, PLAYER_STATISTICS_DUPLICATE_TEAM, PLAYER_STATISTICS_MISSING_FIXTURE_TEAM, PLAYER_STATISTICS_PLAYER_OBJECT_NULL, PLAYER_STATISTICS_PLAYER_REFERENCE_OBJECT_NULL, PLAYER_ID_MISSING, PLAYER_NAME_MISSING, PLAYER_DUPLICATED_IN_TEAM, PLAYER_STATISTICS_LIST_NULL, PLAYER_STATISTICS_LIST_EMPTY, PLAYER_STATISTICS_ENTRY_OBJECT_NULL, PLAYER_STATISTICS_ENTRY_COUNT_UNEXPECTED, PLAYER_MINUTES_INVALID, PLAYER_NUMBER_INVALID, PLAYER_POSITION_UNKNOWN, PLAYER_RATING_INVALID, MULTIPLE_CAPTAINS_IN_TEAM, NEGATIVE_PLAYER_STAT_VALUE, SHOTS_ON_EXCEED_TOTAL, PASSES_ACCURATE_EXCEED_TOTAL, DUELS_WON_EXCEED_TOTAL, DRIBBLES_SUCCESS_EXCEED_ATTEMPTS, OUTFIELD_PLAYER_GOALKEEPER_STAT_INVALID;
        override val value: String get() = name
    }

    private companion object {
        const val MAX_PLAYER_MINUTES = 140L
        const val MAX_PLAYER_NUMBER = 99L
        val positions = setOf("G", "D", "M", "F")
        val outfieldPositions = setOf("D", "M", "F")
    }
}
