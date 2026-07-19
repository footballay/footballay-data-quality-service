package com.footballay.dataquality.pipeline

import com.fasterxml.jackson.databind.JsonNode
import com.footballay.dataquality.inspection.FoundIssue
import com.footballay.dataquality.pipeline.input.QualityCheckRequest

fun interface QualityChecker {
    fun check(
        request: QualityCheckRequest,
        rawJson: JsonNode,
    ): List<FoundIssue>
}
