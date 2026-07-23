package com.footballay.dataquality.pipeline.processing

import com.footballay.dataquality.inspection.FoundIssue
import com.footballay.dataquality.inspection.validateInspectionRuleCode
import com.footballay.dataquality.logger
import com.footballay.dataquality.pipeline.QualityChecker
import com.footballay.dataquality.pipeline.idempotency.RawEventIdempotencyChecker
import com.footballay.dataquality.pipeline.input.QualityCheckRequest
import com.footballay.dataquality.pipeline.input.QualityParameter
import com.footballay.dataquality.pipeline.input.RawCollectedEvent
import com.footballay.dataquality.pipeline.input.DataProvider
import com.footballay.dataquality.pipeline.port.QualityResultPublisher
import com.footballay.dataquality.pipeline.port.QualityResultStore
import com.footballay.dataquality.pipeline.rawresponse.RawResponseReader
import com.footballay.dataquality.pipeline.result.QualityIssueEvidence
import com.footballay.dataquality.pipeline.result.QualityIssueSeverity
import com.footballay.dataquality.pipeline.result.QualityIssueWrite
import com.footballay.dataquality.pipeline.result.QualityResponseLocation
import com.footballay.dataquality.pipeline.result.QualityResultWrite
import java.time.Clock
import java.util.Locale

class DataQualityInspectionPipelineService(
    private val idempotencyChecker: RawEventIdempotencyChecker,
    private val rawResponseReader: RawResponseReader,
    private val qualityChecker: QualityChecker,
    private val resultStore: QualityResultStore,
    private val resultPublisher: QualityResultPublisher,
    private val clock: Clock,
) : DataQualityInspectionPipeline {
    private val log = logger()

    override fun process(event: RawCollectedEvent) {
        try {
            if (!idempotencyChecker.shouldProcess(event.rawEventId)) {
                return
            }

            val rawJson = rawResponseReader.read(event.rawJsonObjectKey)
            val foundIssues = qualityChecker.check(event.toQualityCheckRequest(), rawJson)
            val savedResult = resultStore.save(createWrite(event, foundIssues))
            resultPublisher.publish(savedResult)
        } catch (exception: Exception) {
            log.error(
                "Data quality pipeline processing failed. rawEventId={}, provider={}, endpointKey={}",
                event.rawEventId,
                event.provider,
                event.endpointKey,
                exception,
            )
            throw exception
        }
    }

    private fun RawCollectedEvent.toQualityCheckRequest(): QualityCheckRequest =
        QualityCheckRequest(
            provider = provider,
            endpointKey = endpointKey,
            parameters = parameters.groupBy(QualityParameter::name, QualityParameter::value),
            collectedAt = collectedAt,
        )

    private fun createWrite(
        event: RawCollectedEvent,
        foundIssues: List<FoundIssue>,
    ): QualityResultWrite =
        QualityResultWrite(
            rawEventId = event.rawEventId,
            provider = event.provider,
            endpointKey = event.endpointKey,
            parameters = event.parameters,
            canonicalHash = event.canonicalHash,
            rawJsonObjectKey = event.rawJsonObjectKey,
            checkedAt = clock.instant(),
            inspectionVersion = INSPECTION_VERSION,
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

    private companion object {
        const val INSPECTION_VERSION = "V1"
    }
}
