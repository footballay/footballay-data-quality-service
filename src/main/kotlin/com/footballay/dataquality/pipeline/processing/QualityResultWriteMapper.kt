package com.footballay.dataquality.pipeline.processing

import com.footballay.dataquality.inspection.FoundIssue
import com.footballay.dataquality.inspection.validateInspectionRuleCode
import com.footballay.dataquality.pipeline.input.DataProvider
import com.footballay.dataquality.pipeline.input.RawCollectedEvent
import com.footballay.dataquality.pipeline.result.QualityIssueEvidence
import com.footballay.dataquality.pipeline.result.QualityIssueSeverity
import com.footballay.dataquality.pipeline.result.QualityIssueWrite
import com.footballay.dataquality.pipeline.result.QualityResponseLocation
import com.footballay.dataquality.pipeline.result.QualityResultWrite
import java.time.Instant
import java.util.Locale

/** Inspector의 발견 결과를 Store 경계의 쓰기 입력으로 변환한다. */
fun interface QualityResultWriteMapper {
    fun map(
        event: RawCollectedEvent,
        checkedAt: Instant,
        inspectionVersion: Int,
        foundIssues: List<FoundIssue>,
    ): QualityResultWrite
}

class DefaultQualityResultWriteMapper : QualityResultWriteMapper {
    override fun map(
        event: RawCollectedEvent,
        checkedAt: Instant,
        inspectionVersion: Int,
        foundIssues: List<FoundIssue>,
    ): QualityResultWrite =
        QualityResultWrite(
            rawEventId = event.rawEventId,
            provider = event.provider,
            endpointKey = event.endpointKey,
            parameters = event.parameters,
            canonicalHash = event.canonicalHash,
            rawJsonObjectKey = event.rawJsonObjectKey,
            collectedAt = event.collectedAt,
            checkedAt = checkedAt,
            inspectionVersion = inspectionVersion,
            issues = foundIssues.map { it.toWrite(event) },
        )

    private fun FoundIssue.toWrite(event: RawCollectedEvent): QualityIssueWrite =
        QualityIssueWrite(
            suggestedTypeCode = suggestedType,
            inspectionCode = toInspectionCode(event),
            severity = QualityIssueSeverity.valueOf(severity.name),
            title = title,
            responseLocation = QualityResponseLocation(responseLocation.section, responseLocation.path),
            evidence = evidence?.let { QualityIssueEvidence(it.values) },
        )

    private fun FoundIssue.toInspectionCode(event: RawCollectedEvent): String {
        validateInspectionRuleCode(inspectionRuleCode.value)
        return listOf(event.provider.toCodeValue(), event.endpointKey.toCodeValue(), inspectionName.value, inspectionRuleCode.value)
            .joinToString(":")
    }

    private fun DataProvider.toCodeValue(): String = name.replace("_", "")

    private fun String.toCodeValue(): String =
        replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .uppercase(Locale.ROOT)
}
