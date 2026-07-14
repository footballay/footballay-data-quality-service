package com.footballay.footballaydataqualityservice.pipeline

import com.fasterxml.jackson.databind.JsonNode

fun interface QualityChecker {
    fun check(
        event: RawCollectedEvent,
        rawJson: JsonNode,
    ): QualityScanResult
}
