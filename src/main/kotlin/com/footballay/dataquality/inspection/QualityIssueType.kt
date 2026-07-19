package com.footballay.dataquality.inspection

/** 데이터 품질 문제를 서비스 전체에서 일관되게 분류하는 유형이다. */
enum class QualityIssueType {
    UNEXPECTED_NULL,
    UNEXPECTED_BLANK,
    INVALID_FORMAT,
    OUT_OF_RANGE,
    INCONSISTENT_INFORMATION,
    UNEXPECTED_RESPONSE,
    UPSTREAM_ERROR,
}
