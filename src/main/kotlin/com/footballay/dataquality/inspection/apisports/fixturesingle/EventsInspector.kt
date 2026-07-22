package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.footballay.dataquality.inspection.FoundIssue
import com.footballay.dataquality.inspection.InspectionEvidence
import com.footballay.dataquality.inspection.InspectionName
import com.footballay.dataquality.inspection.InspectionRuleCode
import com.footballay.dataquality.inspection.InspectionSeverity
import com.footballay.dataquality.inspection.QualityInspector
import com.footballay.dataquality.inspection.QualityIssueType

/**
 * API Sports Fixture Single 경기 이벤트의 기본 무결성과 provider 값 변화를 검사한다.
 *
 * Substitution N의 N은 리그별 교체 제한과 무관하게 양의 정수면 유효하게 처리한다.
 */
class EventsInspector : QualityInspector<ApiSportsFixtureSingleRootDto> {
    override val inspectionName = InspectionName.of("EVENTS")

    override fun inspect(target: ApiSportsFixtureSingleRootDto): List<FoundIssue> {
        val findings = mutableListOf<FoundIssue>()
        target.response.orEmpty().forEachIndexed { responseIndex, response ->
            if (response == null) return@forEachIndexed

            val homeTeamId = response.teams?.home?.id?.takeIf { it > 0 }
            val awayTeamId = response.teams?.away?.id?.takeIf { it > 0 }
            val fixtureTeamIds =
                if (homeTeamId != null && awayTeamId != null && homeTeamId != awayTeamId) {
                    setOf(homeTeamId, awayTeamId)
                } else {
                    emptySet()
                }
            response.events.orEmpty().forEachIndexed eventLoop@{ eventIndex, event ->
                val path = "$.response[$responseIndex].events[$eventIndex]"
                if (event == null) {
                    findings +=
                        issue(
                            Code.EVENT_OBJECT_NULL,
                            QualityIssueType.UNEXPECTED_NULL,
                            InspectionSeverity.CRITICAL,
                            "Event object is null",
                            path,
                            fixtureSingleEvidence("eventIndex" to eventIndex.toString()),
                        )
                    return@eventLoop
                }
                inspectCommon(findings, event, path, eventIndex, fixtureTeamIds)
                if (event.type.isNullOrBlank()) {
                    return@eventLoop
                }
                when (event.type) {
                    "Goal" -> inspectGoal(findings, event, path, eventIndex)
                    "Card" -> inspectCard(findings, event, path, eventIndex)
                    "subst" -> inspectSubstitution(findings, event, path, eventIndex)
                    "Var" -> Unit
                    else -> findings += issue(Code.UNKNOWN_EVENT_TYPE, QualityIssueType.UNEXPECTED_RESPONSE, InspectionSeverity.WARN, "Unknown event type", "$path.type", evidence(eventIndex, event))
                }
            }
        }
        return findings
    }

    private fun inspectCommon(
        findings: MutableList<FoundIssue>,
        event: ApiSportsFixtureSingleEventDto,
        path: String,
        eventIndex: Int,
        fixtureTeamIds: Set<Long>,
    ) {
        val teamId = event.team?.id
        when {
            teamId == null || teamId <= 0 -> findings += issue(Code.EVENT_TEAM_ID_MISSING, if (teamId == null) QualityIssueType.UNEXPECTED_NULL else QualityIssueType.OUT_OF_RANGE, InspectionSeverity.ERROR, "Event team ID is missing or invalid", "$path.team.id", evidence(eventIndex, event))
            fixtureTeamIds.isNotEmpty() && teamId !in fixtureTeamIds -> findings += issue(Code.EVENT_TEAM_NOT_FIXTURE_TEAM, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.ERROR, "Event team is not a fixture team", "$path.team.id", evidence(eventIndex, event))
        }
        val time = event.time
        if (time?.elapsed == null) findings += issue(Code.EVENT_TIME_MISSING, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.WARN, "Event elapsed time is missing", "$path.time.elapsed", evidence(eventIndex, event))
        if (time?.elapsed != null && time.elapsed < 0) findings += invalidTimeIssue(path, eventIndex, event, "elapsed", time.elapsed)
        if (time?.extra != null && time.extra < 0) findings += invalidTimeIssue(path, eventIndex, event, "extra", time.extra)
        when {
            event.type == null -> findings += issue(Code.EVENT_TYPE_MISSING, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "Event type is missing", "$path.type", evidence(eventIndex, event))
            event.type.isBlank() -> findings += issue(Code.EVENT_TYPE_MISSING, QualityIssueType.UNEXPECTED_BLANK, InspectionSeverity.ERROR, "Event type is blank", "$path.type", evidence(eventIndex, event))
        }
        if (!event.type.isNullOrBlank()) {
            when {
                event.detail == null -> findings += issue(Code.EVENT_DETAIL_MISSING, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.WARN, "Event detail is missing", "$path.detail", evidence(eventIndex, event))
                event.detail.isBlank() -> findings += issue(Code.EVENT_DETAIL_MISSING, QualityIssueType.UNEXPECTED_BLANK, InspectionSeverity.WARN, "Event detail is blank", "$path.detail", evidence(eventIndex, event))
            }
        }
    }

    private fun inspectGoal(findings: MutableList<FoundIssue>, event: ApiSportsFixtureSingleEventDto, path: String, eventIndex: Int) {
        if (!event.player.hasReference()) {
            findings += issue(Code.GOAL_PLAYER_REFERENCE_MISSING, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "Goal player reference is missing", "$path.player", evidence(eventIndex, event))
        } else if (event.player.hasMissingOrInvalidId()) {
            findings += playerIdIssue(Code.GOAL_PLAYER_ID_MISSING, "Goal player ID is missing or invalid", "$path.player.id", eventIndex, event, event.player)
        }
        inspectGoalAssist(findings, event, path, eventIndex)
    }

    private fun inspectCard(findings: MutableList<FoundIssue>, event: ApiSportsFixtureSingleEventDto, path: String, eventIndex: Int) {
        if (!event.player.hasReference()) {
            findings += issue(Code.CARD_PLAYER_REFERENCE_MISSING, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "Card player reference is missing", "$path.player", evidence(eventIndex, event))
        } else if (event.player.hasMissingOrInvalidId()) {
            findings += playerIdIssue(Code.CARD_PLAYER_ID_MISSING_OR_INVALID, "Card player ID is missing or invalid", "$path.player.id", eventIndex, event, event.player)
        }
        if (!event.detail.isNullOrBlank() && event.detail !in knownCardDetails) findings += issue(Code.UNKNOWN_CARD_DETAIL, QualityIssueType.UNEXPECTED_RESPONSE, InspectionSeverity.WARN, "Unknown card detail", "$path.detail", evidence(eventIndex, event))
    }

    private fun inspectSubstitution(findings: MutableList<FoundIssue>, event: ApiSportsFixtureSingleEventDto, path: String, eventIndex: Int) {
        if (!event.player.hasReference()) {
            findings += issue(Code.SUBSTITUTION_OUT_PLAYER_MISSING, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "Substitution out player is missing", "$path.player", evidence(eventIndex, event))
        } else if (event.player.hasMissingOrInvalidId()) {
            findings += playerIdIssue(Code.SUBSTITUTION_OUT_PLAYER_ID_MISSING_OR_INVALID, "Substitution out player ID is missing or invalid", "$path.player.id", eventIndex, event, event.player)
        }
        if (!event.assist.hasReference()) {
            findings += issue(Code.SUBSTITUTION_IN_PLAYER_MISSING, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, "Substitution in player is missing", "$path.assist", evidence(eventIndex, event))
        } else if (event.assist.hasMissingOrInvalidId()) {
            findings += playerIdIssue(Code.SUBSTITUTION_IN_PLAYER_ID_MISSING_OR_INVALID, "Substitution in player ID is missing or invalid", "$path.assist.id", eventIndex, event, event.assist)
        }
        val outPlayerId = event.player?.id?.takeIf { it > 0 }
        val inPlayerId = event.assist?.id?.takeIf { it > 0 }
        if (outPlayerId != null && outPlayerId == inPlayerId) findings += issue(Code.SUBSTITUTION_SAME_PLAYER, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.ERROR, "Substitution players have the same ID", path, evidence(eventIndex, event))

        // API Sports의 Substitution N은 리그별 교체 제한과 무관하게 양의 정수면 유효하다.
        val number = substitutionNumber(event.detail)
        if (event.detail?.isNotBlank() == true && (number == null || number <= 0)) {
            val suggestedType = if (number == null) QualityIssueType.INVALID_FORMAT else QualityIssueType.OUT_OF_RANGE
            findings += issue(Code.SUBSTITUTION_DETAIL_INVALID, suggestedType, InspectionSeverity.ERROR, "Substitution detail is invalid", "$path.detail", evidence(eventIndex, event))
        }
    }

    private fun invalidTimeIssue(path: String, eventIndex: Int, event: ApiSportsFixtureSingleEventDto, field: String, value: Long): FoundIssue =
        issue(Code.EVENT_TIME_INVALID, QualityIssueType.OUT_OF_RANGE, InspectionSeverity.ERROR, "Event time is negative", "$path.time.$field", evidence(eventIndex, event, "actualValue" to value.toString()))

    private fun inspectGoalAssist(
        findings: MutableList<FoundIssue>,
        event: ApiSportsFixtureSingleEventDto,
        path: String,
        eventIndex: Int,
    ) {
        val assist = event.assist ?: return
        val assistId = assist.id
        val assistName = assist.name
        when {
            assistId == null && !assistName.isNullOrBlank() ->
                findings += issue(Code.GOAL_ASSIST_REFERENCE_PARTIAL, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.WARN, "Goal assist ID is missing", "$path.assist.id", evidence(eventIndex, event))
            assistId != null && assistId <= 0 ->
                findings += issue(Code.GOAL_ASSIST_REFERENCE_PARTIAL, QualityIssueType.OUT_OF_RANGE, InspectionSeverity.WARN, "Goal assist ID is invalid", "$path.assist.id", evidence(eventIndex, event, "assistId" to assistId.toString()))
            assistId != null && assistId > 0 && assistName == null ->
                findings += issue(Code.GOAL_ASSIST_REFERENCE_PARTIAL, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.WARN, "Goal assist name is missing", "$path.assist.name", evidence(eventIndex, event))
            assistId != null && assistId > 0 && assistName?.isBlank() == true ->
                findings += issue(Code.GOAL_ASSIST_REFERENCE_PARTIAL, QualityIssueType.UNEXPECTED_BLANK, InspectionSeverity.WARN, "Goal assist name is blank", "$path.assist.name", evidence(eventIndex, event))
        }
    }

    private fun playerIdIssue(code: Code, title: String, path: String, eventIndex: Int, event: ApiSportsFixtureSingleEventDto, person: ApiSportsFixtureSingleEventPersonDto?): FoundIssue =
        issue(code, if (person?.id == null) QualityIssueType.UNEXPECTED_NULL else QualityIssueType.OUT_OF_RANGE, InspectionSeverity.WARN, title, path, evidence(eventIndex, event, "playerId" to person?.id?.toString()))

    private fun issue(code: Code, suggestedType: QualityIssueType, severity: InspectionSeverity, title: String, path: String, evidence: InspectionEvidence): FoundIssue =
        fixtureSingleFoundIssue(suggestedType, inspectionName, code, severity, title, "events", path, evidence)

    private fun evidence(eventIndex: Int, event: ApiSportsFixtureSingleEventDto, vararg extra: Pair<String, String?>): InspectionEvidence =
        fixtureSingleEvidence("eventIndex" to eventIndex.toString(), "eventType" to event.type, "eventDetail" to event.detail, "elapsed" to event.time?.elapsed?.toString(), "extra" to event.time?.extra?.toString(), *extra)

    private fun ApiSportsFixtureSingleEventPersonDto?.hasName(): Boolean = !this?.name.isNullOrBlank()

    private fun ApiSportsFixtureSingleEventPersonDto?.hasValidId(): Boolean = this?.id != null && id > 0

    private fun ApiSportsFixtureSingleEventPersonDto?.hasReference(): Boolean = this?.id != null || hasName()

    private fun ApiSportsFixtureSingleEventPersonDto?.hasMissingOrInvalidId(): Boolean = this?.id == null || id <= 0

    private fun substitutionNumber(detail: String?): Long? = substitutionPattern.matchEntire(detail ?: "")?.groupValues?.get(1)?.toLongOrNull()

    private enum class Code : InspectionRuleCode {
        EVENT_OBJECT_NULL, EVENT_TEAM_ID_MISSING, EVENT_TEAM_NOT_FIXTURE_TEAM, EVENT_TIME_MISSING, EVENT_TIME_INVALID, EVENT_TYPE_MISSING, EVENT_DETAIL_MISSING, UNKNOWN_EVENT_TYPE,
        GOAL_PLAYER_REFERENCE_MISSING, GOAL_PLAYER_ID_MISSING, GOAL_ASSIST_REFERENCE_PARTIAL,
        CARD_PLAYER_REFERENCE_MISSING, CARD_PLAYER_ID_MISSING_OR_INVALID, UNKNOWN_CARD_DETAIL,
        SUBSTITUTION_OUT_PLAYER_MISSING, SUBSTITUTION_IN_PLAYER_MISSING, SUBSTITUTION_OUT_PLAYER_ID_MISSING_OR_INVALID, SUBSTITUTION_IN_PLAYER_ID_MISSING_OR_INVALID, SUBSTITUTION_SAME_PLAYER, SUBSTITUTION_DETAIL_INVALID,
        ;

        override val value: String get() = name
    }

    private companion object {
        val knownCardDetails = setOf("Yellow Card", "Red Card", "Yellow-Red Card")
        val substitutionPattern = Regex("Substitution (-?\\d+)")
    }
}
