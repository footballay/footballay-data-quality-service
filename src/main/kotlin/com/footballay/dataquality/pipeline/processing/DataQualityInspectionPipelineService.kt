package com.footballay.dataquality.pipeline.processing

import com.footballay.dataquality.logger
import com.footballay.dataquality.pipeline.QualityChecker
import com.footballay.dataquality.pipeline.idempotency.RawEventIdempotencyChecker
import com.footballay.dataquality.pipeline.input.QualityCheckRequest
import com.footballay.dataquality.pipeline.input.QualityParameter
import com.footballay.dataquality.pipeline.input.RawCollectedEvent
import com.footballay.dataquality.pipeline.port.QualityResultPublisher
import com.footballay.dataquality.pipeline.port.QualityResultStore
import com.footballay.dataquality.pipeline.rawresponse.RawResponseReader
import java.time.Clock

class DataQualityInspectionPipelineService(
    private val idempotencyChecker: RawEventIdempotencyChecker,
    private val rawResponseReader: RawResponseReader,
    private val qualityChecker: QualityChecker,
    private val qualityResultWriteMapper: QualityResultWriteMapper,
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
            val savedResult =
                resultStore.save(
                    qualityResultWriteMapper.map(
                        event = event,
                        checkedAt = clock.instant(),
                        inspectionVersion = qualityChecker.inspectionVersion,
                        foundIssues = foundIssues,
                    ),
                )
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

}
