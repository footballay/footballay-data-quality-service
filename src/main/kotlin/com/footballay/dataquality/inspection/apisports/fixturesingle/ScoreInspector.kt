package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.footballay.dataquality.inspection.FoundIssue
import com.footballay.dataquality.inspection.InspectionEvidence
import com.footballay.dataquality.inspection.InspectionName
import com.footballay.dataquality.inspection.InspectionRuleCode
import com.footballay.dataquality.inspection.InspectionSeverity
import com.footballay.dataquality.inspection.QualityInspector
import com.footballay.dataquality.inspection.QualityIssueType

/** API Sports Fixture Single goals와 단계별 score의 기본 무결성을 검사한다. */
class ScoreInspector : QualityInspector<ApiSportsFixtureSingleRootDto> {
    override val inspectionName = InspectionName.of("SCORE")

    override fun inspect(target: ApiSportsFixtureSingleRootDto): List<FoundIssue> {
        val findings = mutableListOf<FoundIssue>()

        target.response.orEmpty().forEachIndexed { index, response ->
            if (response == null) return@forEachIndexed

            val basePath = "$.response[$index]"
            val statusShort = response.fixture?.status?.short
            val goals = response.goals
            val score = response.score

            if (goals == null) {
                findings += objectNullIssue(Code.GOALS_OBJECT_NULL, "Goals object is null", "$basePath.goals")
            } else {
                inspectPair(
                    findings,
                    Code.GOALS_PARTIAL,
                    Code.GOALS_NEGATIVE,
                    goals,
                    "$basePath.goals",
                    "goals",
                    reportPartial = !FixtureStatusSemantics.isFinished(statusShort),
                )
                if (FixtureStatusSemantics.isFinished(statusShort) && !goals.hasBothValues()) {
                    findings +=
                        issue(
                            Code.FINISHED_WITHOUT_FINAL_GOALS,
                            QualityIssueType.UNEXPECTED_NULL,
                            InspectionSeverity.ERROR,
                            "Finished fixture has no complete final goals",
                            "$basePath.goals",
                            evidence("statusShort" to statusShort, "homeGoals" to goals.home.toStringOrNull(), "awayGoals" to goals.away.toStringOrNull()),
                        )
                }
            }

            if (score == null) {
                findings += objectNullIssue(Code.SCORE_OBJECT_NULL, "Score object is null", "$basePath.score")
                return@forEachIndexed
            }

            inspectPair(findings, Code.SCORE_PAIR_PARTIAL, Code.SCORE_NEGATIVE, score.halftime, "$basePath.score.halftime", "halftime")
            inspectPair(findings, Code.SCORE_PAIR_PARTIAL, Code.SCORE_NEGATIVE, score.fulltime, "$basePath.score.fulltime", "fulltime")
            inspectPair(findings, Code.SCORE_PAIR_PARTIAL, Code.SCORE_NEGATIVE, score.extratime, "$basePath.score.extratime", "extratime")
            inspectPair(findings, Code.SCORE_PAIR_PARTIAL, Code.SCORE_NEGATIVE, score.penalty, "$basePath.score.penalty", "penalty")

            if (FixtureStatusSemantics.isFinished(statusShort) && score.halftime.hasBothValidValues() && score.fulltime.hasBothValidValues()) {
                val halftime = score.halftime!!
                val fulltime = score.fulltime!!
                if (halftime.home!! > fulltime.home!! || halftime.away!! > fulltime.away!!) {
                    findings +=
                        issue(
                            Code.HALFTIME_SCORE_EXCEEDS_FULLTIME,
                            QualityIssueType.INCONSISTENT_INFORMATION,
                            InspectionSeverity.ERROR,
                            "Halftime score exceeds fulltime score",
                            "$basePath.score",
                            evidence("statusShort" to statusShort, "halftime" to halftime.display(), "fulltime" to fulltime.display()),
                        )
                }
            }

            if (FixtureStatusSemantics.isRegularTimeFinished(statusShort) && goals.hasBothValidValues() && score.fulltime.hasBothValidValues() && goals != score.fulltime) {
                val finalGoals = goals!!
                val fulltime = score.fulltime!!
                findings +=
                    issue(
                        Code.REGULAR_TIME_SCORE_TOTAL_MISMATCH,
                        QualityIssueType.INCONSISTENT_INFORMATION,
                        InspectionSeverity.ERROR,
                        "Goals and fulltime score do not match",
                        "$basePath.score.fulltime",
                        evidence("statusShort" to statusShort, "goals" to finalGoals.display(), "fulltime" to fulltime.display()),
                    )
            }
            if (FixtureStatusSemantics.isExtraTimeFinished(statusShort) && goals.hasBothValidValues() && score.fulltime.hasBothValidValues() && score.extratime.hasBothValidValues()) {
                val finalGoals = goals!!
                val fulltime = score.fulltime!!
                val extratime = score.extratime!!
                if (finalGoals.home != fulltime.home!! + extratime.home!! || finalGoals.away != fulltime.away!! + extratime.away!!) {
                    findings +=
                        issue(
                            Code.EXTRATIME_SCORE_TOTAL_MISMATCH,
                            QualityIssueType.INCONSISTENT_INFORMATION,
                            InspectionSeverity.WARN,
                            "Goals do not match fulltime and extratime scores",
                            "$basePath.score.extratime",
                            evidence("statusShort" to statusShort, "goals" to finalGoals.display(), "fulltime" to fulltime.display(), "extratime" to extratime.display()),
                        )
                }
            }
            if (FixtureStatusSemantics.isPenaltyShootoutFinished(statusShort) && score.penalty.hasBothValidValues()) {
                val penalty = score.penalty!!
                if (penalty.home == penalty.away) findings +=
                    issue(
                        Code.PENALTY_SCORE_TIED,
                        QualityIssueType.INCONSISTENT_INFORMATION,
                        InspectionSeverity.ERROR,
                        "Penalty shootout score is tied",
                        "$basePath.score.penalty",
                        evidence("statusShort" to statusShort, "penalty" to penalty.display()),
                    )
            }
        }

        return findings
    }

    private fun inspectPair(
        findings: MutableList<FoundIssue>,
        partialCode: Code,
        negativeCode: Code,
        pair: ApiSportsFixtureSingleScorePairDto?,
        path: String,
        phase: String,
        reportPartial: Boolean = true,
    ) {
        pair ?: return
        if (reportPartial && (pair.home == null) != (pair.away == null)) {
            findings += issue(partialCode, QualityIssueType.INCONSISTENT_INFORMATION, InspectionSeverity.WARN, "Score pair is partial", path, evidence("phase" to phase, "home" to pair.home.toStringOrNull(), "away" to pair.away.toStringOrNull()))
        }
        if (pair.home != null && pair.home < 0) findings += negativeIssue(negativeCode, "$path.home", phase, "HOME", pair.home)
        if (pair.away != null && pair.away < 0) findings += negativeIssue(negativeCode, "$path.away", phase, "AWAY", pair.away)
    }

    private fun negativeIssue(code: Code, path: String, phase: String, side: String, value: Long): FoundIssue =
        issue(code, QualityIssueType.OUT_OF_RANGE, InspectionSeverity.ERROR, "Score is negative", path, evidence("phase" to phase, "side" to side, "actualValue" to value.toString()))

    private fun objectNullIssue(code: Code, title: String, path: String): FoundIssue =
        issue(code, QualityIssueType.UNEXPECTED_NULL, InspectionSeverity.ERROR, title, path, null)

    private fun issue(code: Code, suggestedType: QualityIssueType, severity: InspectionSeverity, title: String, path: String, evidence: InspectionEvidence?): FoundIssue =
        fixtureSingleFoundIssue(suggestedType, inspectionName, code, severity, title, "score", path, evidence)

    private fun evidence(vararg entries: Pair<String, String?>): InspectionEvidence = fixtureSingleEvidence(*entries)

    private fun ApiSportsFixtureSingleScorePairDto?.hasBothValues(): Boolean = this?.home != null && this.away != null

    private fun ApiSportsFixtureSingleScorePairDto?.hasBothValidValues(): Boolean = hasBothValues() && this!!.home!! >= 0 && away!! >= 0

    private fun ApiSportsFixtureSingleScorePairDto.display(): String = "${home}:${away}"

    private fun Long?.toStringOrNull(): String? = this?.toString()

    private enum class Code : InspectionRuleCode {
        GOALS_OBJECT_NULL,
        SCORE_OBJECT_NULL,
        GOALS_PARTIAL,
        GOALS_NEGATIVE,
        SCORE_PAIR_PARTIAL,
        SCORE_NEGATIVE,
        FINISHED_WITHOUT_FINAL_GOALS,
        HALFTIME_SCORE_EXCEEDS_FULLTIME,
        REGULAR_TIME_SCORE_TOTAL_MISMATCH,
        EXTRATIME_SCORE_TOTAL_MISMATCH,
        PENALTY_SCORE_TIED,
        ;

        override val value: String get() = name
    }
}
