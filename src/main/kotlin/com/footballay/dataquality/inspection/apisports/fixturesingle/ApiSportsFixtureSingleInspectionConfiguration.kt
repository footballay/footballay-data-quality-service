package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.dataquality.inspection.QualityInspectionProcessor
import com.footballay.dataquality.inspection.QualityInspector
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order

@Configuration
@ConditionalOnProperty(prefix = "footballay.data-quality.pipeline", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(LineupAvailabilityProperties::class)
class ApiSportsFixtureSingleInspectionConfiguration {
    @Bean
    fun apiSportsFixtureSingleInspectionProcessor(
        objectMapper: ObjectMapper,
        responseInspectors: List<QualityInspector<ApiSportsFixtureSingleRootDto>>,
        requestResponseInspectors: List<QualityInspector<FixtureSingleRequestResponse>>,
        lineupAvailabilityInspectors: List<QualityInspector<FixtureSingleLineupAvailabilityTarget>>,
    ): QualityInspectionProcessor =
        ApiSportsFixtureSingleInspectionProcessor(
            objectMapper = objectMapper,
            responseInspectors = responseInspectors,
            requestResponseInspectors = requestResponseInspectors,
            lineupAvailabilityInspectors = lineupAvailabilityInspectors,
        )

    @Bean
    @Order(100)
    fun apiSportsFixtureSingleResponseInspector(): QualityInspector<ApiSportsFixtureSingleRootDto> =
        ApiSportsFixtureSingleResponseInspector()

    @Bean
    @Order(200)
    fun fixtureInspector(): QualityInspector<ApiSportsFixtureSingleRootDto> = FixtureInspector()

    @Bean
    @Order(300)
    fun fixtureStatusInspector(): QualityInspector<ApiSportsFixtureSingleRootDto> = FixtureStatusInspector()

    @Bean
    @Order(400)
    fun leagueInspector(): QualityInspector<ApiSportsFixtureSingleRootDto> = LeagueInspector()

    @Bean
    @Order(500)
    fun teamsInspector(): QualityInspector<ApiSportsFixtureSingleRootDto> = TeamsInspector()

    @Bean
    @Order(600)
    fun scoreInspector(): QualityInspector<ApiSportsFixtureSingleRootDto> = ScoreInspector()

    @Bean
    @Order(700)
    fun eventsInspector(): QualityInspector<ApiSportsFixtureSingleRootDto> = EventsInspector()

    @Bean
    @Order(800)
    fun lineupsInspector(): QualityInspector<ApiSportsFixtureSingleRootDto> = LineupsInspector()

    @Bean
    @Order(900)
    fun teamStatisticsInspector(): QualityInspector<ApiSportsFixtureSingleRootDto> = TeamStatisticsInspector()

    @Bean
    @Order(850)
    fun lineupAvailabilityInspector(properties: LineupAvailabilityProperties): QualityInspector<FixtureSingleLineupAvailabilityTarget> =
        LineupAvailabilityInspector(properties)

    @Bean
    @Order(100)
    fun fixtureSingleRequestResponseInspector(): QualityInspector<FixtureSingleRequestResponse> =
        FixtureSingleRequestResponseInspector()
}
