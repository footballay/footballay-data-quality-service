package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.footballay.dataquality.inspection.FoundIssue
import com.footballay.dataquality.inspection.InspectionEvidence
import com.footballay.dataquality.inspection.InspectionRuleCode
import com.footballay.dataquality.inspection.InspectionSeverity
import com.footballay.dataquality.inspection.InspectionName
import com.footballay.dataquality.inspection.QualityInspector
import com.footballay.dataquality.inspection.QualityIssueType

/** API Sports Fixture Single home·away team의 기본 무결성을 검사한다. */
class TeamsInspector : QualityInspector<ApiSportsFixtureSingleRootDto> {
    override val inspectionName = InspectionName.of("TEAMS")

    override fun inspect(target: ApiSportsFixtureSingleRootDto): List<FoundIssue> {
        val findings = mutableListOf<FoundIssue>()

        target.response.orEmpty().forEachIndexed { index, response ->
            if (response == null) {
                return@forEachIndexed
            }

            val path = "$.response[$index].teams"
            val teams = response.teams
            if (teams == null) {
                findings +=
                    issue(
                        Code.OBJECT_NULL,
                        QualityIssueType.UNEXPECTED_NULL,
                        InspectionSeverity.CRITICAL,
                        "Teams object is null",
                        path,
                        evidence("responseIndex" to index.toString()),
                    )
                return@forEachIndexed
            }
            val home = teams.home
            val away = teams.away

            if (home == null) {
                findings +=
                    issue(
                        Code.HOME_TEAM_MISSING,
                        QualityIssueType.UNEXPECTED_NULL,
                        InspectionSeverity.ERROR,
                        "Home team is null",
                        "$path.home",
                        evidence("side" to "HOME"),
                    )
            } else {
                inspectTeam(findings, home, "HOME", "$path.home")
            }
            if (away == null) {
                findings +=
                    issue(
                        Code.AWAY_TEAM_MISSING,
                        QualityIssueType.UNEXPECTED_NULL,
                        InspectionSeverity.ERROR,
                        "Away team is null",
                        "$path.away",
                        evidence("side" to "AWAY"),
                    )
            } else {
                inspectTeam(findings, away, "AWAY", "$path.away")
            }
            if (home?.id != null && home.id > 0 && away?.id != null && away.id > 0 && home.id == away.id) {
                findings +=
                    issue(
                        Code.SAME_TEAM_ON_BOTH_SIDES,
                        QualityIssueType.INCONSISTENT_INFORMATION,
                        InspectionSeverity.ERROR,
                        "Home and away teams have the same ID",
                        path,
                        evidence("homeTeamId" to home.id.toString(), "awayTeamId" to away.id.toString()),
                    )
            }
            if (home?.winner == true && away?.winner == true) {
                findings +=
                    issue(
                        Code.BOTH_TEAMS_MARKED_WINNER,
                        QualityIssueType.INCONSISTENT_INFORMATION,
                        InspectionSeverity.ERROR,
                        "Home and away teams are both marked as winners",
                        path,
                        evidence("homeWinner" to "true", "awayWinner" to "true"),
                    )
            }
        }

        return findings
    }

    private fun inspectTeam(
        findings: MutableList<FoundIssue>,
        team: ApiSportsFixtureSingleTeamDto,
        side: String,
        path: String,
    ) {
        when {
            team.id == null ->
                findings +=
                    issue(
                        Code.TEAM_ID_MISSING_OR_INVALID,
                        QualityIssueType.UNEXPECTED_NULL,
                        InspectionSeverity.ERROR,
                        "Team ID is null",
                        "$path.id",
                        evidence("side" to side, "teamId" to null),
                    )
            team.id <= 0 ->
                findings +=
                    issue(
                        Code.TEAM_ID_MISSING_OR_INVALID,
                        QualityIssueType.OUT_OF_RANGE,
                        InspectionSeverity.ERROR,
                        "Team ID is not positive",
                        "$path.id",
                        evidence("side" to side, "teamId" to team.id.toString()),
                    )
        }
        when {
            team.name == null ->
                findings +=
                    issue(
                        Code.TEAM_NAME_MISSING,
                        QualityIssueType.UNEXPECTED_NULL,
                        InspectionSeverity.WARN,
                        "Team name is null",
                        "$path.name",
                        evidence("side" to side, "teamName" to null),
                    )
            team.name.isBlank() ->
                findings +=
                    issue(
                        Code.TEAM_NAME_MISSING,
                        QualityIssueType.UNEXPECTED_BLANK,
                        InspectionSeverity.WARN,
                        "Team name is blank",
                        "$path.name",
                        evidence("side" to side, "teamName" to team.name),
                    )
        }
    }

    private fun issue(
        code: Code,
        suggestedType: QualityIssueType,
        severity: InspectionSeverity,
        title: String,
        path: String,
        evidence: InspectionEvidence,
    ): FoundIssue =
        fixtureSingleFoundIssue(suggestedType, inspectionName, code, severity, title, "teams", path, evidence)

    private fun evidence(vararg entries: Pair<String, String?>): InspectionEvidence = fixtureSingleEvidence(*entries)

    private enum class Code : InspectionRuleCode {
        OBJECT_NULL,
        HOME_TEAM_MISSING,
        AWAY_TEAM_MISSING,
        TEAM_ID_MISSING_OR_INVALID,
        TEAM_NAME_MISSING,
        SAME_TEAM_ON_BOTH_SIDES,
        BOTH_TEAMS_MARKED_WINNER,
        ;

        override val value: String
            get() = name
    }
}
