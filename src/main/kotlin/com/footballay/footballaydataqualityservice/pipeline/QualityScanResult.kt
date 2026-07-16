package com.footballay.footballaydataqualityservice.pipeline

import com.footballay.footballaydataqualityservice.inspection.InspectionFinding

data class QualityScanResult(
    val scannerVersion: String,
    val findings: List<InspectionFinding> = emptyList(),
)
