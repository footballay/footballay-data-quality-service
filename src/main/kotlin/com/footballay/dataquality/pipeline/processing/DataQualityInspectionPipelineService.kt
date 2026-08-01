package com.footballay.dataquality.pipeline.processing

import com.footballay.dataquality.logger
import com.footballay.dataquality.pipeline.QualityChecker
import com.footballay.dataquality.pipeline.idempotency.RawEventProcessingRegistry
import com.footballay.dataquality.pipeline.input.QualityCheckRequest
import com.footballay.dataquality.pipeline.input.QualityParameter
import com.footballay.dataquality.pipeline.input.RawCollectedEvent
import com.footballay.dataquality.pipeline.port.QualityResultPublisher
import com.footballay.dataquality.pipeline.port.QualityResultStore
import com.footballay.dataquality.pipeline.rawresponse.RawResponseReader
import com.footballay.dataquality.pipeline.result.QualityResult
import java.time.Clock

class DataQualityInspectionPipelineService(
    private val processingRegistry: RawEventProcessingRegistry,
    private val rawResponseReader: RawResponseReader,
    private val qualityChecker: QualityChecker,
    private val qualityResultWriteMapper: QualityResultWriteMapper,
    private val resultStore: QualityResultStore,
    private val resultPublisher: QualityResultPublisher,
    private val clock: Clock,
) : DataQualityInspectionPipeline {
    private val log = logger()

    override fun process(event: RawCollectedEvent) {
        val claim = processingRegistry.tryClaim(event.rawEventId) ?: return

        try {
            val rawJson = rawResponseReader.read(event.rawJsonObjectKey)
            val foundIssues = qualityChecker.check(event.toQualityCheckRequest(), rawJson)
            val savedResult = resultStore.save(
                    qualityResultWriteMapper.map(
                        event = event,
                        checkedAt = clock.instant(),
                        inspectionVersion = qualityChecker.inspectionVersion,
                        foundIssues = foundIssues,
                    ),
                )
            publishResult(savedResult)
            processingRegistry.complete(claim)
        } catch (exception: Exception) {
            processingRegistry.release(claim)
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

    private fun publishResult(result: QualityResult) {
        try {
            resultPublisher.publish(result)
        } catch (exception: Exception) {
            log.error(
                "QualityResultPublisher unexpectedly threw while requesting publication. resultId={}, rawEventId={}, provider={}, endpointKey={}",
                result.resultId,
                result.rawEventId,
                result.provider,
                result.endpointKey,
                exception,
            )
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
