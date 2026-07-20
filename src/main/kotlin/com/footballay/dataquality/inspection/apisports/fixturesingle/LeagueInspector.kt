package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.footballay.dataquality.inspection.FoundIssue
import com.footballay.dataquality.inspection.InspectionRuleCode
import com.footballay.dataquality.inspection.InspectionSeverity
import com.footballay.dataquality.inspection.InspectionName
import com.footballay.dataquality.inspection.QualityInspector
import com.footballay.dataquality.inspection.QualityIssueType

/** API Sports Fixture Single league와 season, round의 기본 무결성을 검사한다. */
class LeagueInspector : QualityInspector<ApiSportsFixtureSingleRootDto> {
    override val inspectionName = InspectionName.of("LEAGUE")

    override fun inspect(target: ApiSportsFixtureSingleRootDto): List<FoundIssue> {
        val findings = mutableListOf<FoundIssue>()

        target.response.orEmpty().forEachIndexed { index, response ->
            if (response == null) {
                return@forEachIndexed
            }

            val path = "$.response[$index].league"
            val league = response.league
            if (league == null) {
                findings +=
                    issue(
                        Code.OBJECT_NULL,
                        QualityIssueType.UNEXPECTED_NULL,
                        InspectionSeverity.CRITICAL,
                        "League object is null",
                        path,
                        evidence("responseIndex" to index.toString()),
                    )
                return@forEachIndexed
            }

            when {
                league.id == null ->
                    findings += issue(
                        Code.LEAGUE_ID_MISSING_OR_INVALID,
                        QualityIssueType.UNEXPECTED_NULL,
                        InspectionSeverity.ERROR,
                        "League ID is null",
                        "$path.id",
                        evidence("leagueId" to null),
                    )
                league.id <= 0 ->
                    findings += issue(
                        Code.LEAGUE_ID_MISSING_OR_INVALID,
                        QualityIssueType.OUT_OF_RANGE,
                        InspectionSeverity.ERROR,
                        "League ID is not positive",
                        "$path.id",
                        evidence("leagueId" to league.id.toString()),
                    )
            }
            when {
                league.name == null ->
                    findings += issue(
                        Code.LEAGUE_NAME_MISSING,
                        QualityIssueType.UNEXPECTED_NULL,
                        InspectionSeverity.WARN,
                        "League name is null",
                        "$path.name",
                        evidence("leagueName" to null),
                    )
                league.name.isBlank() ->
                    findings += issue(
                        Code.LEAGUE_NAME_MISSING,
                        QualityIssueType.UNEXPECTED_BLANK,
                        InspectionSeverity.WARN,
                        "League name is blank",
                        "$path.name",
                        evidence("leagueName" to league.name),
                    )
            }
            when {
                league.season == null ->
                    findings += issue(
                        Code.LEAGUE_SEASON_INVALID,
                        QualityIssueType.UNEXPECTED_NULL,
                        InspectionSeverity.WARN,
                        "League season is null",
                        "$path.season",
                        evidence("season" to null),
                    )
                league.season !in MIN_SEASON..MAX_SEASON ->
                    findings += issue(
                        Code.LEAGUE_SEASON_INVALID,
                        QualityIssueType.OUT_OF_RANGE,
                        InspectionSeverity.WARN,
                        "League season is outside the supported range",
                        "$path.season",
                        evidence("season" to league.season.toString()),
                    )
            }

            val statusShort = response.fixture?.status?.short
            if (statusShort in ROUND_REQUIRED_STATUS_SHORTS) {
                when {
                    league.round == null ->
                        findings += issue(
                            Code.LEAGUE_ROUND_MISSING,
                            QualityIssueType.UNEXPECTED_NULL,
                            InspectionSeverity.WARN,
                            "League round is null after the match has started",
                            "$path.round",
                            evidence("round" to null, "statusShort" to statusShort),
                        )
                    league.round.isBlank() ->
                        findings += issue(
                            Code.LEAGUE_ROUND_MISSING,
                            QualityIssueType.UNEXPECTED_BLANK,
                            InspectionSeverity.WARN,
                            "League round is blank after the match has started",
                            "$path.round",
                            evidence("round" to league.round, "statusShort" to statusShort),
                        )
                }
            }
        }

        return findings
    }

    private fun issue(
        code: Code,
        suggestedType: QualityIssueType,
        severity: InspectionSeverity,
        title: String,
        path: String,
        evidence: com.footballay.dataquality.inspection.InspectionEvidence,
    ): FoundIssue =
        fixtureSingleFoundIssue(suggestedType, inspectionName, code, severity, title, "league", path, evidence)

    private fun evidence(vararg entries: Pair<String, String?>) = fixtureSingleEvidence(*entries)

    private enum class Code : InspectionRuleCode {
        OBJECT_NULL,
        LEAGUE_ID_MISSING_OR_INVALID,
        LEAGUE_NAME_MISSING,
        LEAGUE_SEASON_INVALID,
        LEAGUE_ROUND_MISSING,
        ;

        override val value: String
            get() = name
    }

    private companion object {
        const val MIN_SEASON = 1900
        const val MAX_SEASON = 2100
        val ROUND_REQUIRED_STATUS_SHORTS =
            setOf(
                "1H",
                "HT",
                "2H",
                "ET",
                "BT",
                "P",
                "SUSP",
                "INT",
                "LIVE",
                "FT",
                "AET",
                "PEN",
                "ABD",
            )
    }
}
