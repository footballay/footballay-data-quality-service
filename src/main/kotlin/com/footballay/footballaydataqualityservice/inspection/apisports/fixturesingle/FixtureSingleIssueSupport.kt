package com.footballay.footballaydataqualityservice.inspection.apisports.fixturesingle

import com.footballay.footballaydataqualityservice.inspection.FoundIssue
import com.footballay.footballaydataqualityservice.inspection.InspectionEvidence
import com.footballay.footballaydataqualityservice.inspection.InspectionRuleCode
import com.footballay.footballaydataqualityservice.inspection.InspectionResponseLocation
import com.footballay.footballaydataqualityservice.inspection.InspectionSeverity
import com.footballay.footballaydataqualityservice.inspection.InspectionName
import com.footballay.footballaydataqualityservice.inspection.QualityIssueType

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
