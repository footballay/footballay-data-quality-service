package com.footballay.footballaydataqualityservice.pipeline

import com.fasterxml.jackson.databind.JsonNode

class AlwaysHappyQualityChecker : QualityChecker {
    override fun check(
        event: RawCollectedEvent,
        rawJson: JsonNode,
    ): QualityScanResult = QualityScanResult(scannerVersion = "always-happy-v1")
}
