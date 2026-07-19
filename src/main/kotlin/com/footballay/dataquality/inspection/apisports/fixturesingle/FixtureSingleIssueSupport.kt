package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.footballay.dataquality.inspection.FoundIssue
import com.footballay.dataquality.inspection.InspectionEvidence
import com.footballay.dataquality.inspection.InspectionRuleCode
import com.footballay.dataquality.inspection.InspectionResponseLocation
import com.footballay.dataquality.inspection.InspectionSeverity
import com.footballay.dataquality.inspection.InspectionName
import com.footballay.dataquality.inspection.QualityIssueType

internal fun fixtureSingleFoundIssue(
    suggestedType: QualityIssueType,
    inspectionName: InspectionName,
    inspectionRuleCode: InspectionRuleCode,
    severity: InspectionSeverity,
    title: String,
    section: String,
    path: String,
    evidence: InspectionEvidence? = null,
): FoundIssue =
    FoundIssue(
        suggestedType = suggestedType,
        inspectionName = inspectionName,
        inspectionRuleCode = inspectionRuleCode,
        severity = severity,
        title = title,
        responseLocation = InspectionResponseLocation(section, path),
        evidence = evidence,
    )

internal fun fixtureSingleEvidence(vararg entries: Pair<String, String?>): InspectionEvidence =
    InspectionEvidence.of(*entries)
