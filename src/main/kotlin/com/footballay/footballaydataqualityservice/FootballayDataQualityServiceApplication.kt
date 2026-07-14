package com.footballay.footballaydataqualityservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class FootballayDataQualityServiceApplication

fun main(args: Array<String>) {
    runApplication<FootballayDataQualityServiceApplication>(*args)
}
