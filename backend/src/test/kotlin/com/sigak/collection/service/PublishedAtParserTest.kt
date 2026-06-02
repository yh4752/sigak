package com.sigak.collection.service

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class PublishedAtParserTest {

    @Test
    fun normalizeReturnsIsoInstantWhenInputIsAlreadyIso() {
        assertEquals(
            "2026-05-05T09:00:00Z",
            PublishedAtParser.normalizeToIsoString("2026-05-05T09:00:00Z")
        )
    }

    @Test
    fun normalizeConvertsRfc1123DateToIsoInstant() {
        assertEquals(
            "2026-05-05T09:00:00Z",
            PublishedAtParser.normalizeToIsoString("Tue, 05 May 2026 09:00:00 GMT")
        )
    }

    @Test
    fun normalizeKeepsBlankAndUnparseableValuesUnchanged() {
        assertEquals("", PublishedAtParser.normalizeToIsoString("   "))
        assertEquals("not-a-date", PublishedAtParser.normalizeToIsoString("not-a-date"))
    }

    @Test
    fun parseOrEpochReturnsEpochWhenInputCannotBeParsed() {
        assertEquals(Instant.EPOCH, PublishedAtParser.parseOrEpoch("not-a-date"))
    }
}
