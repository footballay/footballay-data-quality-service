package com.footballay.footballaydataqualityservice.inspection.apisports.fixturesingle

import com.footballay.footballaydataqualityservice.inspection.FoundIssue
import com.footballay.footballaydataqualityservice.inspection.InspectionCode
import com.footballay.footballaydataqualityservice.inspection.InspectionEvidence
import com.footballay.footballaydataqualityservice.inspection.InspectionResponseLocation
import com.footballay.footballaydataqualityservice.inspection.InspectionSeverity

internal fun fixtureSingleFoundIssue(
    inspectionCode: InspectionCode,
    severity: InspectionSeverity,
    title: String,
    section: String,
    path: String,
    evidence: InspectionEvidence? = null,
): FoundIssue =
    FoundIssue(
        inspectionCode = inspectionCode,
        severity = severity,
        title = title,
        responseLocation = InspectionResponseLocation(section, path),
        evidence = evidence,
    )

internal fun fixtureSingleEvidence(vararg entries: Pair<String, String?>): InspectionEvidence =
    InspectionEvidence.of(*entries)
