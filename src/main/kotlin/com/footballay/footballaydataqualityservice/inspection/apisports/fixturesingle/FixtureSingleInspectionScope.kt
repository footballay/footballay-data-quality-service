package com.footballay.footballaydataqualityservice.inspection.apisports.fixturesingle

import com.footballay.footballaydataqualityservice.inspection.InspectionScope

/** API Sports Fixture Single 검사에서 유지할 논리적 검사 영역이다. */
internal enum class FixtureSingleInspectionScope : InspectionScope {
    ROOT,
    RAW_MAPPING,
    FIXTURE,
    FIXTURE_STATUS,
    REQUEST_RESPONSE,
    ;

    override val value: String
        get() = name
}
