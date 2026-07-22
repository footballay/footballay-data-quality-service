package com.footballay.dataquality.inspection.apisports.fixturesingle

/** API Sports Fixture Single status short 값을 검사 규칙에서 일관되게 분류한다. */
internal object FixtureStatusSemantics {
    private val startedStatusShorts =
        setOf("1H", "HT", "2H", "ET", "BT", "P", "SUSP", "INT", "LIVE", "FT", "AET", "PEN", "ABD")

    fun hasStarted(statusShort: String?): Boolean = statusShort in startedStatusShorts

    fun isRegularTimeFinished(statusShort: String?): Boolean = statusShort == "FT"

    fun isExtraTimeFinished(statusShort: String?): Boolean = statusShort == "AET" || statusShort == "PEN"

    fun isPenaltyShootoutFinished(statusShort: String?): Boolean = statusShort == "PEN"

    fun isFinished(statusShort: String?): Boolean =
        isRegularTimeFinished(statusShort) || isExtraTimeFinished(statusShort)
}
