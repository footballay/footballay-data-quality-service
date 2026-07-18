package com.footballay.footballaydataqualityservice.pipeline

import com.fasterxml.jackson.databind.JsonNode
import com.footballay.footballaydataqualityservice.inspection.FoundIssue
import com.footballay.footballaydataqualityservice.pipeline.input.QualityCheckRequest

fun interface QualityChecker {
    fun check(
        request: QualityCheckRequest,
        rawJson: JsonNode,
    ): List<FoundIssue>
}
