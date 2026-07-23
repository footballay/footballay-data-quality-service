package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.footballay.dataquality.inspection.FoundIssue
import com.footballay.dataquality.inspection.InspectionName
import com.footballay.dataquality.inspection.InspectionRuleCode
import com.footballay.dataquality.inspection.QualityInspector
import com.footballay.dataquality.inspection.QualityIssueType
import java.time.Instant
import java.time.temporal.ChronoUnit

/** 리그별 제공 시점 정책에 따라 FixtureSingle 라인업의 존재 여부만 검사한다. */
class LineupAvailabilityInspector(
    private val properties: LineupAvailabilityProperties,
) : QualityInspector<FixtureSingleLineupAvailabilityTarget> {
    override val inspectionName = InspectionName.of("LINEUP_AVAILABILITY")

    override fun inspect(target: FixtureSingleLineupAvailabilityTarget): List<FoundIssue> {
        val collectedAt = target.collectedAt ?: return emptyList()

        return buildList {
            target.fixtureSingle.response.orEmpty().forEachIndexed responseLoop@{ responseIndex, response ->
                if (response == null || !response.lineups.isNullOrEmpty()) return@responseLoop

                val leagueId = response.league?.id?.takeIf { it > 0 } ?: return@responseLoop
                val rule = properties.leagues[leagueId] ?: return@responseLoop
                val kickoff = response.fixture?.timestamp.toKickoffInstantOrNull() ?: return@responseLoop
                val statusShort = response.fixture?.status?.short
                if (FixtureStatusSemantics.hasStarted(statusShort)) {
                    add(issue(Code.LINEUP_NOT_AVAILABLE_AFTER_KICKOFF, rule, responseIndex, leagueId, kickoff, collectedAt, statusShort))
                    return@responseLoop
                }
                if (statusShort != "NS") return@responseLoop

                val beforeKickoffThreshold = kickoff.minus(rule.expectedBeforeKickoffMinutes, ChronoUnit.MINUTES)
                if (collectedAt.isBefore(beforeKickoffThreshold)) return@responseLoop

                val notStartedGraceDeadline = kickoff.plus(properties.notStartedGraceMinutes, ChronoUnit.MINUTES)
                val code =
                    if (collectedAt.isBefore(notStartedGraceDeadline)) {
                        Code.LINEUP_NOT_AVAILABLE_BEFORE_KICKOFF
                    } else {
                        Code.LINEUP_NOT_AVAILABLE_AFTER_KICKOFF
                    }
                add(issue(code, rule, responseIndex, leagueId, kickoff, collectedAt, statusShort))
            }
        }
    }

    private fun Long?.toKickoffInstantOrNull(): Instant? =
        takeIf { it != null && it > 0 }
            ?.let { timestamp -> runCatching { Instant.ofEpochSecond(timestamp) }.getOrNull() }

    private fun issue(
        code: Code,
        rule: LineupAvailabilityProperties.LeagueRule,
        responseIndex: Int,
        leagueId: Long,
        kickoff: Instant,
        collectedAt: Instant,
        statusShort: String?,
    ): FoundIssue {
        val afterKickoff = code == Code.LINEUP_NOT_AVAILABLE_AFTER_KICKOFF
        return fixtureSingleFoundIssue(
            suggestedType = QualityIssueType.INCONSISTENT_INFORMATION,
            inspectionName = inspectionName,
            inspectionRuleCode = code,
            severity = if (afterKickoff) rule.missingAfterKickoffSeverity else rule.missingBeforeKickoffSeverity,
            title = if (afterKickoff) "Lineup is not available after kickoff" else "Lineup is not available before kickoff",
            section = "lineups",
            path = "$.response[$responseIndex].lineups",
            evidence =
                fixtureSingleEvidence(
                    "responseIndex" to responseIndex.toString(),
                    "leagueId" to leagueId.toString(),
                    "kickoff" to kickoff.toString(),
                    "collectedAt" to collectedAt.toString(),
                    "expectedBeforeKickoffMinutes" to rule.expectedBeforeKickoffMinutes.toString(),
                    "notStartedGraceMinutes" to properties.notStartedGraceMinutes.toString(),
                    "statusShort" to statusShort,
                ),
        )
    }

    private enum class Code : InspectionRuleCode {
        LINEUP_NOT_AVAILABLE_BEFORE_KICKOFF,
        LINEUP_NOT_AVAILABLE_AFTER_KICKOFF,
        ;

        override val value: String
            get() = name
    }
}
