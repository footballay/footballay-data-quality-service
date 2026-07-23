package com.footballay.dataquality.inspection.apisports.fixturesingle

import java.time.Instant

/** 라인업 제공 시점 검사를 위해 FixtureSingle 응답과 실제 수집 시각을 함께 전달한다. */
data class FixtureSingleLineupAvailabilityTarget(
    val fixtureSingle: ApiSportsFixtureSingleRootDto,
    val collectedAt: Instant?,
)
