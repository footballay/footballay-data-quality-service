package com.footballay.dataquality.pipeline

import com.fasterxml.jackson.databind.JsonNode
import com.footballay.dataquality.inspection.FoundIssue
import com.footballay.dataquality.pipeline.input.QualityCheckRequest

interface QualityChecker {
    /** 이 Checker가 적용하는 검사 정책 세대다. */
    val inspectionVersion: Int

    fun check(
        request: QualityCheckRequest,
        rawJson: JsonNode,
    ): List<FoundIssue>
}
