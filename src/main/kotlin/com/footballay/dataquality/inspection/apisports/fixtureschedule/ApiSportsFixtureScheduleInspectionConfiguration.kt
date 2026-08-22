package com.footballay.dataquality.inspection.apisports.fixtureschedule

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.dataquality.inspection.QualityInspectionProcessor
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(prefix = "footballay.data-quality.pipeline", name = ["enabled"], havingValue = "true")
class ApiSportsFixtureScheduleInspectionConfiguration {
    @Bean
    fun apiSportsFixtureScheduleInspectionProcessor(objectMapper: ObjectMapper): QualityInspectionProcessor =
        ApiSportsFixtureScheduleInspectionProcessor(objectMapper)
}
