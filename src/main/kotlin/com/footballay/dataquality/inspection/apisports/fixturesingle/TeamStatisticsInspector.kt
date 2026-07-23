package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.fasterxml.jackson.databind.JsonNode
import com.footballay.dataquality.inspection.FoundIssue
import com.footballay.dataquality.inspection.InspectionEvidence
import com.footballay.dataquality.inspection.InspectionName
import com.footballay.dataquality.inspection.InspectionRuleCode
import com.footballay.dataquality.inspection.InspectionSeverity
import com.footballay.dataquality.inspection.QualityInspector
import com.footballay.dataquality.inspection.QualityIssueType
import kotlin.math.abs

/** API Sports Fixture Single의 팀 통계 구조와 통계값 사이의 기본 관계를 검사한다. */
class TeamStatisticsInspector : QualityInspector<ApiSportsFixtureSingleRootDto> {
    override val inspectionName = InspectionName.of("TEAM_STATISTICS")

    override fun inspect(target: ApiSportsFixtureSingleRootDto): List<FoundIssue> {
        val findings = mutableListOf<FoundIssue>()
        target.response.orEmpty().forEachIndexed responseLoop@{ responseIndex, response ->
            if (response == null) return@responseLoop
            val groups = response.statistics.orEmpty()
            if (groups.isEmpty()) return@responseLoop
            val basePath = "$.response[$responseIndex].statistics"
            val fixtureTeamIds = response.fixtureTeamIds()
            val validGroups = mutableListOf<Pair<Int, ApiSportsFixtureSingleTeamStatisticsDto>>()
            val structureSeverity = severityForAvailability(response.fixture?.status?.short)

            if (groups.size != 2) findings += issue(Code.TEAM_STATISTICS_COUNT_INVALID, QualityIssueType.INCONSISTENT_INFORMATION, structureSeverity, "Team statistics group count is not two", basePath, evidence(responseIndex, "actualCount" to groups.size.toString()))
            groups.forEachIndexed groupLoop@{ groupIndex, group ->
                if (group == null) return@groupLoop
                val teamId = group.team?.id?.takeIf { it > 0 }
                if (teamId != null) {
                    validGroups += groupIndex to group
                    if (fixtureTeamIds.isNotEmpty() && teamId !in fixtureTeamIds) findings += issue(Code.TEAM_STATISTICS_UNKNOWN_TEAM, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.ERROR, "Statistics team is not a fixture team", "$basePath[$groupIndex].team.id", evidence(responseIndex, "teamId" to teamId.toString()))
                }
                inspectGroup(findings, responseIndex, groupIndex, group, "$basePath[$groupIndex]")
            }
            val validTeamIds = validGroups.mapNotNull { it.second.team?.id?.takeIf { id -> id > 0 } }
            validTeamIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { teamId ->
                findings += issue(Code.TEAM_STATISTICS_DUPLICATE_TEAM, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.ERROR, "Statistics team is duplicated", basePath, evidence(responseIndex, "teamId" to teamId.toString()))
            }
            if (fixtureTeamIds.size == 2 && validTeamIds.any { it in fixtureTeamIds } && fixtureTeamIds.any { it !in validTeamIds }) {
                findings += issue(Code.TEAM_STATISTICS_MISSING_FIXTURE_TEAM, QualityIssueType.INCONSISTENT_INFORMATION, structureSeverity, "Statistics are missing a fixture team", basePath, evidence(responseIndex, "fixtureTeamIds" to fixtureTeamIds.sorted().joinToString(",")))
            }
            inspectCrossGroupRelations(findings, responseIndex, groups, validGroups, fixtureTeamIds, basePath)
        }
        return findings
    }

    private fun inspectGroup(findings: MutableList<FoundIssue>, responseIndex: Int, groupIndex: Int, group: ApiSportsFixtureSingleTeamStatisticsDto, path: String) {
        val statistics = group.statistics.orEmpty()
        val types = mutableSetOf<String>()
        statistics.forEachIndexed statisticLoop@{ statisticIndex, statistic ->
            if (statistic == null) return@statisticLoop
            val statisticPath = "$path.statistics[$statisticIndex]"
            val type = statistic.type
            if (type.isNullOrBlank()) {
                findings += issue(Code.STATISTIC_TYPE_MISSING, if (type == null) QualityIssueType.UNEXPECTED_NULL else QualityIssueType.UNEXPECTED_BLANK, InspectionSeverity.ERROR, "Statistic type is missing", "$statisticPath.type", evidence(responseIndex, groupIndex))
                return@statisticLoop
            }
            if (!types.add(type)) findings += issue(Code.STATISTIC_TYPE_DUPLICATED, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.ERROR, "Statistic type is duplicated", "$statisticPath.type", evidence(responseIndex, groupIndex, "statisticType" to type))
            if (type !in knownTypes) findings += issue(Code.UNKNOWN_STATISTIC_TYPE, QualityIssueType.UNEXPECTED_RESPONSE, InspectionSeverity.WARN, "Statistic type is unknown", "$statisticPath.type", evidence(responseIndex, groupIndex, "statisticType" to type))
            val numericValue = statistic.value.asNumberOrNull()
            if (type in countTypes && type != "goals_prevented" && numericValue != null && numericValue < 0) findings += issue(Code.STATISTIC_NEGATIVE_VALUE, QualityIssueType.OUT_OF_RANGE, InspectionSeverity.ERROR, "Statistic value is negative", "$statisticPath.value", evidence(responseIndex, groupIndex, "statisticType" to type, "actualValue" to statistic.value?.asText()))
            if (type in percentageTypes) inspectPercentage(findings, responseIndex, groupIndex, type, statistic.value, "$statisticPath.value")
            if (type == "expected_goals") inspectExpectedGoals(findings, responseIndex, groupIndex, statistic.value, "$statisticPath.value")
        }
        val values = uniqueParsedValues(statistics)
        compareNotExceeding(findings, responseIndex, groupIndex, values, "Shots on Goal", "Total Shots", Code.SHOT_COMPONENT_EXCEEDS_TOTAL, path)
        compareNotExceeding(findings, responseIndex, groupIndex, values, "Shots off Goal", "Total Shots", Code.SHOT_COMPONENT_EXCEEDS_TOTAL, path)
        compareNotExceeding(findings, responseIndex, groupIndex, values, "Blocked Shots", "Total Shots", Code.SHOT_COMPONENT_EXCEEDS_TOTAL, path)
        compareNotExceeding(findings, responseIndex, groupIndex, values, "Passes accurate", "Total passes", Code.ACCURATE_PASSES_EXCEED_TOTAL_PASSES, path)
        val totalShots = values["Total Shots"]
        compareSum(findings, responseIndex, groupIndex, totalShots, values["Shots insidebox"], values["Shots outsidebox"], Code.SHOT_LOCATION_TOTAL_MISMATCH, path)
        compareSum(findings, responseIndex, groupIndex, totalShots, values["Shots on Goal"], values["Shots off Goal"], values["Blocked Shots"], Code.SHOT_OUTCOME_TOTAL_MISMATCH, path)
        val totalPasses = values["Total passes"]
        val accuratePasses = values["Passes accurate"]
        val passesPercent = uniqueParsedPercentage(statistics, "Passes %")
        if (totalPasses != null && totalPasses > 0 && accuratePasses != null && passesPercent != null && abs((accuratePasses / totalPasses * 100) - passesPercent) > 1.0) findings += issue(Code.PASSES_PERCENT_INCONSISTENT, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.WARN, "Passes percentage is inconsistent", "$path.statistics", evidence(responseIndex, groupIndex, "totalPasses" to totalPasses.toString(), "accuratePasses" to accuratePasses.toString(), "passesPercent" to passesPercent.toString()))
    }

    private fun inspectCrossGroupRelations(
        findings: MutableList<FoundIssue>,
        responseIndex: Int,
        groups: List<ApiSportsFixtureSingleTeamStatisticsDto?>,
        validGroups: List<Pair<Int, ApiSportsFixtureSingleTeamStatisticsDto>>,
        fixtureTeamIds: Set<Long>,
        path: String,
    ) {
        if (groups.size != 2 || validGroups.size != 2 || fixtureTeamIds.size != 2) return
        val groupTeamIds = validGroups.mapNotNull { it.second.team?.id?.takeIf { id -> id > 0 } }
        if (groupTeamIds.size != 2 || groupTeamIds.toSet().size != 2 || groupTeamIds.toSet() != fixtureTeamIds) return
        val possessions = validGroups.mapNotNull { (index, group) -> uniqueParsedPercentage(group.statistics.orEmpty(), "Ball Possession")?.let { index to it } }
        if (possessions.size == 2 && (possessions.sumOf { it.second } !in 98.0..102.0)) findings += issue(Code.POSSESSION_TOTAL_INVALID, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.WARN, path = path, title = "Possession total is outside the tolerated range", evidence = evidence(responseIndex, "totalPossession" to possessions.sumOf { it.second }.toString()))
    }

    private fun inspectPercentage(findings: MutableList<FoundIssue>, responseIndex: Int, groupIndex: Int, type: String, value: JsonNode?, path: String) {
        val percent = value.percentOrNull()
        if (value.hasValue() && percent == null) findings += issue(Code.PERCENTAGE_FORMAT_INVALID, QualityIssueType.INVALID_FORMAT, InspectionSeverity.WARN, "Percentage value has an invalid format", path, evidence(responseIndex, groupIndex, "statisticType" to type, "actualValue" to value!!.asText()))
        else if (percent != null && percent !in 0.0..100.0) findings += issue(Code.PERCENTAGE_OUT_OF_RANGE, QualityIssueType.OUT_OF_RANGE, InspectionSeverity.ERROR, "Percentage value is out of range", path, evidence(responseIndex, groupIndex, "statisticType" to type, "actualValue" to value!!.asText()))
    }

    private fun inspectExpectedGoals(findings: MutableList<FoundIssue>, responseIndex: Int, groupIndex: Int, value: JsonNode?, path: String) {
        val numeric = value.asNumberOrNull()
        if (value.hasValue() && numeric == null) findings += issue(Code.EXPECTED_GOALS_INVALID, QualityIssueType.INVALID_FORMAT, InspectionSeverity.WARN, "Expected goals has an invalid format", path, evidence(responseIndex, groupIndex, "actualValue" to value!!.asText()))
        else if (numeric != null && numeric < 0) findings += issue(Code.EXPECTED_GOALS_INVALID, QualityIssueType.OUT_OF_RANGE, InspectionSeverity.ERROR, "Expected goals is negative", path, evidence(responseIndex, groupIndex, "actualValue" to value!!.asText()))
    }

    private fun compareNotExceeding(findings: MutableList<FoundIssue>, responseIndex: Int, groupIndex: Int, values: Map<String, Double?>, component: String, total: String, code: Code, path: String) {
        val componentValue = values[component] ?: return
        val totalValue = values[total] ?: return
        if (componentValue > totalValue) findings += issue(code, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.ERROR, "Statistic component exceeds total", "$path.statistics", evidence(responseIndex, groupIndex, "statisticType" to component, "relatedType" to total, "actualValue" to componentValue.toString(), "relatedValue" to totalValue.toString()))
    }

    private fun compareSum(findings: MutableList<FoundIssue>, responseIndex: Int, groupIndex: Int, total: Double?, first: Double?, second: Double?, code: Code, path: String) {
        if (total != null && first != null && second != null && total != first + second) findings += issue(code, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.WARN, "Statistic components do not match total", "$path.statistics", evidence(responseIndex, groupIndex, "total" to total.toString(), "components" to "$first,$second"))
    }

    private fun compareSum(findings: MutableList<FoundIssue>, responseIndex: Int, groupIndex: Int, total: Double?, first: Double?, second: Double?, third: Double?, code: Code, path: String) {
        if (total != null && first != null && second != null && third != null && total != first + second + third) findings += issue(code, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.WARN, "Statistic components do not match total", "$path.statistics", evidence(responseIndex, groupIndex, "total" to total.toString(), "components" to "$first,$second,$third"))
    }

    private fun ApiSportsFixtureSingleResponseDto.fixtureTeamIds(): Set<Long> {
        val home = teams?.home?.id?.takeIf { it > 0 }
        val away = teams?.away?.id?.takeIf { it > 0 }
        return if (home != null && away != null && home != away) setOf(home, away) else emptySet()
    }

    private fun uniqueParsedValues(statistics: List<ApiSportsFixtureSingleStatisticDto?>): Map<String, Double> =
        statistics.filterNotNull().filter { !it.type.isNullOrBlank() }.groupBy { it.type!! }
            .filterValues { it.size == 1 }
            .mapNotNull { (type, entries) -> entries.single().value.asNumberOrNull()?.let { type to it } }
            .toMap()

    private fun uniqueParsedPercentage(statistics: List<ApiSportsFixtureSingleStatisticDto?>, type: String): Double? =
        statistics.filterNotNull().filter { it.type == type }.singleOrNull()?.value.percentOrNull()

    private fun JsonNode?.asNumberOrNull(): Double? = this?.takeIf { !it.isNull }?.asText()?.trim()?.toDoubleOrNull()
    private fun JsonNode?.percentOrNull(): Double? = this?.takeIf { !it.isNull }?.asText()?.trim()?.removeSuffix("%")?.trim()?.toDoubleOrNull()
    private fun JsonNode?.hasValue(): Boolean = this != null && !isNull
    private fun severityForAvailability(statusShort: String?): InspectionSeverity = if (FixtureStatusSemantics.hasStarted(statusShort)) InspectionSeverity.ERROR else InspectionSeverity.WARN
    private fun issue(code: Code, suggestedType: QualityIssueType, severity: InspectionSeverity, title: String, path: String, evidence: InspectionEvidence): FoundIssue = fixtureSingleFoundIssue(suggestedType, inspectionName, code, severity, title, "statistics", path, evidence)
    private fun evidence(responseIndex: Int, vararg values: Pair<String, String?>): InspectionEvidence = fixtureSingleEvidence("responseIndex" to responseIndex.toString(), *values)

    private fun evidence(responseIndex: Int, groupIndex: Int, vararg values: Pair<String, String?>): InspectionEvidence =
        fixtureSingleEvidence("responseIndex" to responseIndex.toString(), "groupIndex" to groupIndex.toString(), *values)

    private enum class Code : InspectionRuleCode {
        TEAM_STATISTICS_COUNT_INVALID, TEAM_STATISTICS_UNKNOWN_TEAM, TEAM_STATISTICS_DUPLICATE_TEAM, TEAM_STATISTICS_MISSING_FIXTURE_TEAM, STATISTIC_TYPE_MISSING, STATISTIC_TYPE_DUPLICATED, UNKNOWN_STATISTIC_TYPE, STATISTIC_NEGATIVE_VALUE, PERCENTAGE_FORMAT_INVALID, PERCENTAGE_OUT_OF_RANGE, POSSESSION_TOTAL_INVALID, ACCURATE_PASSES_EXCEED_TOTAL_PASSES, PASSES_PERCENT_INCONSISTENT, SHOT_COMPONENT_EXCEEDS_TOTAL, SHOT_LOCATION_TOTAL_MISMATCH, SHOT_OUTCOME_TOTAL_MISMATCH, EXPECTED_GOALS_INVALID;
        override val value: String get() = name
    }

    private companion object {
        val percentageTypes = setOf("Ball Possession", "Passes %")
        val countTypes = setOf("Fouls", "Corner Kicks", "Offsides", "Yellow Cards", "Red Cards", "Goalkeeper Saves", "Total passes", "Passes accurate", "Total Shots", "Shots on Goal", "Shots off Goal", "Blocked Shots", "Shots insidebox", "Shots outsidebox", "goals_prevented")
        val knownTypes = countTypes + percentageTypes + setOf("expected_goals")
    }
}
