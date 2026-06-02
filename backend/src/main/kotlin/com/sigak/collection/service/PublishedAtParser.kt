package com.sigak.collection.service

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object PublishedAtParser {

    fun normalizeToIsoString(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) {
            return trimmed
        }

        return parse(trimmed)?.toString() ?: trimmed
    }

    fun parseOrEpoch(value: String): Instant =
        parse(value.trim())
            // MVP 단계에서는 발행일 파싱 실패 기사를 버리지 않고 오래된 기사로 정렬되도록 보존한다.
            ?: Instant.EPOCH

    private fun parse(value: String): Instant? =
        parseInstant(value) ?: parseRfc1123(value)

    private fun parseInstant(value: String): Instant? =
        try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }

    private fun parseRfc1123(value: String): Instant? =
        try {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
}
