package com.footballay.footballaydataqualityservice.pipeline

import com.fasterxml.jackson.databind.JsonNode

fun interface QualityChecker {
    fun check(
        request: QualityCheckRequest,
        rawJson: JsonNode,
    ): QualityScanResult
}

data class QualityCheckRequest(
    val provider: DataProvider,
    val endpointKey: String,
    val parameters: Map<String, List<String>>,
)
