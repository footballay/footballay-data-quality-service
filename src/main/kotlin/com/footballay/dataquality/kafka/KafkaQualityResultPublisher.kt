package com.footballay.dataquality.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.dataquality.logger
import com.footballay.dataquality.pipeline.port.QualityResultPublisher
import com.footballay.dataquality.pipeline.result.QualityResult
import com.footballay.dataquality.pipeline.result.toEvent
import org.springframework.kafka.core.KafkaTemplate

class KafkaQualityResultPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val topic: String,
) : QualityResultPublisher {
    private val log = logger()

    override fun publish(result: QualityResult) {
        try {
            val payload = objectMapper.writeValueAsString(result.toEvent())
            kafkaTemplate.send(topic, result.resultId, payload).whenComplete { _, exception ->
                if (exception == null) {
                    log.info("Published quality-result event. resultId={}, rawEventId={}", result.resultId, result.rawEventId)
                } else {
                    log.error("Quality-result Kafka publication failed. {}", resultLogContext(result), exception)
                }
            }
        } catch (exception: Exception) {
            log.error("Quality-result Kafka publication could not be started. {}", resultLogContext(result), exception)
        }
    }

    private fun resultLogContext(result: QualityResult): String =
        "resultId=${result.resultId}, rawEventId=${result.rawEventId}, provider=${result.provider}, endpointKey=${result.endpointKey}"
}
