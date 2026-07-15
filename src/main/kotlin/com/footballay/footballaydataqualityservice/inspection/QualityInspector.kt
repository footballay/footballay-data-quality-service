package com.footballay.footballaydataqualityservice.inspection

import com.footballay.footballaydataqualityservice.pipeline.QualityIssue

fun interface QualityInspector<T> {
    fun inspect(dto: T): List<QualityIssue>
}
