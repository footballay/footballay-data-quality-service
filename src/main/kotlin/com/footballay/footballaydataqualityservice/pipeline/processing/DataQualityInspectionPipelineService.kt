package com.footballay.footballaydataqualityservice.pipeline.processing

import com.footballay.footballaydataqualityservice.inspection.FoundIssue
import com.footballay.footballaydataqualityservice.inspection.InspectionCode
import com.footballay.footballaydataqualityservice.logger
import com.footballay.footballaydataqualityservice.pipeline.QualityChecker
import com.footballay.footballaydataqualityservice.pipeline.idempotency.RawEventIdempotencyChecker
import com.footballay.footballaydataqualityservice.pipeline.input.QualityCheckRequest
import com.footballay.footballaydataqualityservice.pipeline.input.QualityParameter
import com.footballay.footballaydataqualityservice.pipeline.input.RawCollectedEvent
import com.footballay.footballaydataqualityservice.pipeline.port.QualityResultPublisher
import com.footballay.footballaydataqualityservice.pipeline.port.QualityResultStore
import com.footballay.footballaydataqualityservice.pipeline.rawresponse.RawResponseReader
import com.footballay.footballaydataqualityservice.pipeline.result.QualityIssueEvidence
import com.footballay.footballaydataqualityservice.pipeline.result.QualityIssueSeverity
import com.footballay.footballaydataqualityservice.pipeline.result.QualityIssueWrite
import com.footballay.footballaydataqualityservice.pipeline.result.QualityResponseLocation
import com.footballay.footballaydataqualityservice.pipeline.result.QualityResultWrite
import java.time.Clock

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
            issues = foundIssues.map { it.toWrite() },
        )

    private fun FoundIssue.toWrite(): QualityIssueWrite =
        QualityIssueWrite(
            suggestedTypeCode = inspectionCode.toSuggestedTypeCode(),
            severity = QualityIssueSeverity.valueOf(severity.name),
            title = title,
            responseLocation = QualityResponseLocation(responseLocation.section, responseLocation.path),
            evidence = evidence?.let { QualityIssueEvidence(it.values) },
        )

    private fun InspectionCode.toSuggestedTypeCode(): String = "$provider:$endpoint:$value"

    private companion object {
        const val INSPECTION_VERSION = "V1"
    }
}
