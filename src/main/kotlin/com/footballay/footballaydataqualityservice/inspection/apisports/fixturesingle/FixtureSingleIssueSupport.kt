package com.footballay.footballaydataqualityservice.inspection.apisports.fixturesingle

import com.footballay.footballaydataqualityservice.inspection.InspectionFinding
import com.footballay.footballaydataqualityservice.inspection.InspectionEvidence
import com.footballay.footballaydataqualityservice.inspection.InspectionResponseLocation
import com.footballay.footballaydataqualityservice.inspection.InspectionSeverity

internal fun fixtureSingleFinding(
    code: String,
    severity: InspectionSeverity,
    title: String,
    section: String,
    path: String,
    evidence: InspectionEvidence? = null,
): InspectionFinding =
    InspectionFinding(
        suggestedTypeCode = code,
        severity = severity,
        title = title,
        responseLocation = InspectionResponseLocation(section, path),
        evidence = evidence,
    )

internal fun fixtureSingleEvidence(vararg entries: Pair<String, String?>): InspectionEvidence =
    InspectionEvidence.of(*entries)
