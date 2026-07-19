package com.footballay.dataquality

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
    fromApplication<FootballayDataQualityServiceApplication>().with(TestcontainersConfiguration::class).run(*args)
}
