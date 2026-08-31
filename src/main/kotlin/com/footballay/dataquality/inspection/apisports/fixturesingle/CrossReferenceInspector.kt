package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.footballay.dataquality.inspection.FoundIssue
import com.footballay.dataquality.inspection.InspectionEvidence
import com.footballay.dataquality.inspection.InspectionName
import com.footballay.dataquality.inspection.InspectionRuleCode
import com.footballay.dataquality.inspection.InspectionSeverity
import com.footballay.dataquality.inspection.QualityInspector
import com.footballay.dataquality.inspection.QualityIssueType

/**
 * API Sports Fixture Single의 라인업, 이벤트, 선수 통계 간 ID 참조 정합성을 검사한다.
 *
 * API Sports의 `subst` 이벤트는 `player`와 `assist`에 sub-in/out을 일관되게 넣지 않으므로,
 * 라인업 상태로 두 방향 모두 검사한다.
 */
class CrossReferenceInspector : QualityInspector<ApiSportsFixtureSingleRootDto> {
    override val inspectionName = InspectionName.of("CROSS_REFERENCE")

    override fun inspect(target: ApiSportsFixtureSingleRootDto): List<FoundIssue> {
        val findings = mutableListOf<FoundIssue>()

        target.response.orEmpty().forEachIndexed responseLoop@{ responseIndex, response ->
            if (response == null) return@responseLoop

            val fixtureTeamIds = response.fixtureTeamIds()
            if (fixtureTeamIds.size != 2) return@responseLoop

            val lineups = trustedLineups(response.lineups, fixtureTeamIds)
            val playerStatistics = trustedPlayerStatistics(response.players, fixtureTeamIds)

            inspectLineupAndPlayerStatistics(findings, responseIndex, lineups, playerStatistics)
            inspectEventsAndLineups(findings, responseIndex, response.events, lineups)
            inspectSubstitutionActiveSets(findings, responseIndex, response.events, lineups)
        }

        return findings
    }

    private fun inspectLineupAndPlayerStatistics(
        findings: MutableList<FoundIssue>,
        responseIndex: Int,
        lineups: Map<Long, TrustedLineup>,
        playerStatistics: Map<Long, TrustedPlayerStatistics>,
    ) {
        lineups.forEach { (teamId, lineup) ->
            val statistics = playerStatistics[teamId] ?: return@forEach

            if (statistics.allPlayerIdsComplete) {
                (lineup.allPlayerIds - statistics.allPlayerIds).forEach { playerId ->
                    findings += issue(Code.LINEUP_PLAYER_MISSING_FROM_PLAYER_STATISTICS, "Lineup player is missing from player statistics", "$.response[$responseIndex].lineups[${lineup.index}]", "lineups", evidence(responseIndex, teamId, playerId, "lineupIndex" to lineup.index.toString()))
                }
            }
            if (lineup.usableForMembershipComparison) {
                (statistics.allPlayerIds - lineup.allPlayerIds).forEach { playerId ->
                    findings += issue(Code.PLAYER_STATISTICS_PLAYER_NOT_IN_LINEUP, "Player statistics player is not in lineup", "$.response[$responseIndex].players[${statistics.index}]", "players", evidence(responseIndex, teamId, playerId, "groupIndex" to statistics.index.toString()))
                }
            }
            lineup.startingPlayerIds.forEach { playerId ->
                if (statistics.substituteByPlayerId[playerId] == true) {
                    findings += issue(Code.STARTER_SUBSTITUTE_FLAG_CONFLICT, "Starting lineup player is marked as substitute", "$.response[$responseIndex].players[${statistics.index}]", "players", evidence(responseIndex, teamId, playerId, "groupIndex" to statistics.index.toString()))
                }
            }
            lineup.substitutePlayerIds.forEach { playerId ->
                if (statistics.substituteByPlayerId[playerId] == false) {
                    findings += issue(Code.BENCH_STARTER_FLAG_CONFLICT, "Bench lineup player is marked as starter", "$.response[$responseIndex].players[${statistics.index}]", "players", evidence(responseIndex, teamId, playerId, "groupIndex" to statistics.index.toString()))
                }
            }
        }
    }

    private fun inspectEventsAndLineups(
        findings: MutableList<FoundIssue>,
        responseIndex: Int,
        events: List<ApiSportsFixtureSingleEventDto?>?,
        lineups: Map<Long, TrustedLineup>,
    ) {
        events.orEmpty().forEachIndexed eventLoop@{ eventIndex, event ->
            event ?: return@eventLoop
            val teamId = event.team?.id?.takeIf { it > 0 } ?: return@eventLoop
            val lineup = lineups[teamId] ?: return@eventLoop
            val path = "$.response[$responseIndex].events[$eventIndex]"

            if (event.type == "subst") {
                inspectSubstitutionLineupReferences(findings, responseIndex, eventIndex, event, lineup, path)
                return@eventLoop
            }

            event.player?.id?.takeIf { lineup.usableForMembershipComparison && it > 0 }?.let { playerId ->
                if (playerId !in lineup.allPlayerIds) {
                    findings += issue(Code.EVENT_PLAYER_NOT_IN_LINEUP, "Event player is not in lineup", "$path.player.id", "events", evidence(responseIndex, teamId, playerId, "eventIndex" to eventIndex.toString()))
                }
            }
            event.assist?.id?.takeIf { event.type == "Goal" && lineup.usableForMembershipComparison && it > 0 }?.let { playerId ->
                if (playerId !in lineup.allPlayerIds) {
                    findings += issue(Code.EVENT_ASSIST_NOT_IN_LINEUP, "Event assist is not in lineup", "$path.assist.id", "events", evidence(responseIndex, teamId, playerId, "eventIndex" to eventIndex.toString()))
                }
            }
        }
    }

    private fun inspectSubstitutionLineupReferences(
        findings: MutableList<FoundIssue>,
        responseIndex: Int,
        eventIndex: Int,
        event: ApiSportsFixtureSingleEventDto,
        lineup: TrustedLineup,
        path: String,
    ) {
        val teamId = lineup.teamId
        listOf("player" to event.player?.id, "assist" to event.assist?.id).forEach { (field, id) ->
            val playerId = id?.takeIf { lineup.usableForMembershipComparison && it > 0 } ?: return@forEach
            if (playerId !in lineup.allPlayerIds) {
                findings += issue(Code.SUBSTITUTION_PLAYER_NOT_IN_LINEUP, "Substitution player is not in lineup", "$path.$field.id", "events", evidence(responseIndex, teamId, playerId, "eventIndex" to eventIndex.toString()))
            }
        }
    }

    private fun inspectSubstitutionActiveSets(
        findings: MutableList<FoundIssue>,
        responseIndex: Int,
        events: List<ApiSportsFixtureSingleEventDto?>?,
        lineups: Map<Long, TrustedLineup>,
    ) {
        val activeSets =
            lineups
                .filterValues { lineup -> lineup.startingPlayerIdsComplete && lineup.startingPlayerIds.size == STARTING_XI_SIZE }
                .mapValues { (_, lineup) -> lineup.startingPlayerIds.toMutableSet() }
                .toMutableMap()

        events.orEmpty().forEachIndexed eventLoop@{ eventIndex, event ->
            if (event?.type != "subst") return@eventLoop
            val teamId = event.team?.id?.takeIf { it > 0 } ?: return@eventLoop
            val activeSet = activeSets[teamId] ?: return@eventLoop
            val playerId = event.player?.id?.takeIf { it > 0 }
            val assistId = event.assist?.id?.takeIf { it > 0 }

            if (playerId == null || assistId == null || playerId == assistId) {
                activeSets.remove(teamId)
                return@eventLoop
            }

            val substitution =
                listOf(playerId to assistId, assistId to playerId)
                    .firstOrNull { (outPlayerId, inPlayerId) -> outPlayerId in activeSet && inPlayerId !in activeSet }
            if (substitution == null) {
                findings += issue(Code.SUBSTITUTION_PLAYERS_CANNOT_BE_APPLIED, "Substitution players cannot be applied to the active lineup", "$.response[$responseIndex].events[$eventIndex]", "events", evidence(responseIndex, teamId, playerId, "eventIndex" to eventIndex.toString(), "assistPlayerId" to assistId.toString()))
                activeSets.remove(teamId)
                return@eventLoop
            }

            val (outPlayerId, inPlayerId) = substitution
            activeSet.remove(outPlayerId)
            activeSet.add(inPlayerId)
        }
    }

    private fun trustedLineups(
        lineups: List<ApiSportsFixtureSingleLineupDto?>?,
        fixtureTeamIds: Set<Long>,
    ): Map<Long, TrustedLineup> =
        lineups.orEmpty()
            .mapIndexedNotNull { index, lineup ->
                val teamId = lineup?.team?.id?.takeIf { it in fixtureTeamIds } ?: return@mapIndexedNotNull null
                val startingEntries = lineup.startXI
                val substituteEntries = lineup.substitutes
                val startingPlayerIds = startingEntries.orEmpty().mapNotNull { it?.player?.id?.takeIf { playerId -> playerId > 0 } }
                val substitutePlayerIds = substituteEntries.orEmpty().mapNotNull { it?.player?.id?.takeIf { playerId -> playerId > 0 } }
                val allPlayerIds = startingPlayerIds + substitutePlayerIds
                if (allPlayerIds.size != allPlayerIds.toSet().size) return@mapIndexedNotNull null
                val startingPlayerIdsComplete = startingEntries != null && startingPlayerIds.size == startingEntries.size && startingPlayerIds.size == startingPlayerIds.toSet().size
                val substitutePlayerIdsComplete = substituteEntries != null && substitutePlayerIds.size == substituteEntries.size && substitutePlayerIds.size == substitutePlayerIds.toSet().size
                val allPlayerIdsComplete = startingPlayerIdsComplete && substitutePlayerIdsComplete
                val usableForMembershipComparison = allPlayerIdsComplete && startingPlayerIds.size == STARTING_XI_SIZE
                TrustedLineup(teamId, index, startingPlayerIds.toSet(), substitutePlayerIds.toSet(), allPlayerIdsComplete, startingPlayerIdsComplete, substitutePlayerIdsComplete, usableForMembershipComparison)
            }
            .groupBy { it.teamId }
            .filterValues { it.size == 1 }
            .mapValues { (_, values) -> values.single() }

    private fun trustedPlayerStatistics(
        groups: List<ApiSportsFixtureSinglePlayerStatisticsTeamDto?>?,
        fixtureTeamIds: Set<Long>,
    ): Map<Long, TrustedPlayerStatistics> =
        groups.orEmpty()
            .mapIndexedNotNull { index, group ->
                val teamId = group?.team?.id?.takeIf { it in fixtureTeamIds } ?: return@mapIndexedNotNull null
                val players = group.players
                val playerEntries = players.orEmpty().mapNotNull { playerStatistics ->
                    val playerId = playerStatistics?.player?.id?.takeIf { it > 0 } ?: return@mapNotNull null
                    playerId to playerStatistics
                }
                if (playerEntries.size != playerEntries.map { it.first }.toSet().size) return@mapIndexedNotNull null
                val allPlayerIdsComplete = players != null && playerEntries.size == players.size && playerEntries.size == playerEntries.map { it.first }.toSet().size
                val substituteByPlayerId = playerEntries.mapNotNull { (playerId, playerStatistics) ->
                    val entries = playerStatistics.statistics
                    if (entries?.size != 1) return@mapNotNull null
                    entries.single()?.games?.substitute?.let { playerId to it }
                }.toMap()
                TrustedPlayerStatistics(teamId, index, playerEntries.map { it.first }.toSet(), allPlayerIdsComplete, substituteByPlayerId)
            }
            .groupBy { it.teamId }
            .filterValues { it.size == 1 }
            .mapValues { (_, values) -> values.single() }

    private fun ApiSportsFixtureSingleResponseDto.fixtureTeamIds(): Set<Long> {
        val homeTeamId = teams?.home?.id?.takeIf { it > 0 }
        val awayTeamId = teams?.away?.id?.takeIf { it > 0 }
        return if (homeTeamId != null && awayTeamId != null && homeTeamId != awayTeamId) setOf(homeTeamId, awayTeamId) else emptySet()
    }

    private fun issue(code: Code, title: String, path: String, section: String, evidence: InspectionEvidence): FoundIssue =
        fixtureSingleFoundIssue(QualityIssueType.INCONSISTENT_INFORMATION, inspectionName, code, InspectionSeverity.WARN, title, section, path, evidence)

    private fun evidence(responseIndex: Int, teamId: Long, playerId: Long, vararg additional: Pair<String, String?>): InspectionEvidence =
        fixtureSingleEvidence("responseIndex" to responseIndex.toString(), "teamId" to teamId.toString(), "playerId" to playerId.toString(), *additional)

    private data class TrustedLineup(
        val teamId: Long,
        val index: Int,
        val startingPlayerIds: Set<Long>,
        val substitutePlayerIds: Set<Long>,
        val allPlayerIdsComplete: Boolean,
        val startingPlayerIdsComplete: Boolean,
        val substitutePlayerIdsComplete: Boolean,
        val usableForMembershipComparison: Boolean,
    ) {
        val allPlayerIds: Set<Long> = startingPlayerIds + substitutePlayerIds
    }

    private data class TrustedPlayerStatistics(
        val teamId: Long,
        val index: Int,
        val allPlayerIds: Set<Long>,
        val allPlayerIdsComplete: Boolean,
        val substituteByPlayerId: Map<Long, Boolean>,
    )

    private enum class Code : InspectionRuleCode {
        LINEUP_PLAYER_MISSING_FROM_PLAYER_STATISTICS,
        PLAYER_STATISTICS_PLAYER_NOT_IN_LINEUP,
        STARTER_SUBSTITUTE_FLAG_CONFLICT,
        BENCH_STARTER_FLAG_CONFLICT,
        EVENT_PLAYER_NOT_IN_LINEUP,
        EVENT_ASSIST_NOT_IN_LINEUP,
        SUBSTITUTION_PLAYER_NOT_IN_LINEUP,
        SUBSTITUTION_PLAYERS_CANNOT_BE_APPLIED,
        ;

        override val value: String
            get() = name
    }

    private companion object {
        const val STARTING_XI_SIZE = 11
    }
}
