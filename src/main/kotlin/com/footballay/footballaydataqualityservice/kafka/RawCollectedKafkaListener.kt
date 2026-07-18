package com.footballay.footballaydataqualityservice.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.footballaydataqualityservice.logger
import com.footballay.footballaydataqualityservice.pipeline.input.RawCollectedEvent
import com.footballay.footballaydataqualityservice.pipeline.processing.DataQualityInspectionPipeline
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "footballay.data-quality.pipeline", name = ["enabled"], havingValue = "true")
class RawCollectedKafkaListener(
    private val objectMapper: ObjectMapper,
    private val inspectionPipeline: DataQualityInspectionPipeline,
) {
    private val log = logger()

    @KafkaListener(
        topics = ["\${footballay.data-quality.pipeline.raw-collected-topic}"],
        groupId = "\${footballay.data-quality.pipeline.consumer-group}",
    )
    fun consume(payload: String) {
        // TODO: DataProvider 변환 실패는 재시도해도 성공하지 않으므로 DLT 처리 정책을 추가한다.
        val event = objectMapper.readValue(payload, RawCollectedEvent::class.java)
        log.info("Consuming raw-collected event. rawEventId={}", event.rawEventId)
        inspectionPipeline.process(event)
    }
}
