package com.hcmcbustracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LicensePlateNormalizerTest {

    @Test
    fun normalize_removesHyphensDotsAndSpaces() {
        assertEquals("50E78427", LicensePlateNormalizer.normalize("50E-784.27"))
        assertEquals("51B12345", LicensePlateNormalizer.normalize("51B 123.45"))
        assertEquals("53N4567", LicensePlateNormalizer.normalize("53n-4567"))
        assertEquals("", LicensePlateNormalizer.normalize(null))
        assertEquals("", LicensePlateNormalizer.normalize("   "))
    }

    @Test
    fun extractNumericTail_extractsDigitsAtEnd() {
        assertEquals(78427, LicensePlateNormalizer.extractNumericTail("50E-784.27"))
        assertEquals(12345, LicensePlateNormalizer.extractNumericTail("51B-123.45"))
        assertEquals(4567, LicensePlateNormalizer.extractNumericTail("53N-4567"))
        assertEquals(99, LicensePlateNormalizer.extractNumericTail("99"))
        assertNull(LicensePlateNormalizer.extractNumericTail("ABC"))
        assertNull(LicensePlateNormalizer.extractNumericTail(null))
    }

    @Test
    fun isValidPlateFormat_validatesVietnameseBusPlatePatterns() {
        assertTrue(LicensePlateNormalizer.isValidPlateFormat("50E-784.27"))
        assertTrue(LicensePlateNormalizer.isValidPlateFormat("51B-123.45"))
        assertTrue(LicensePlateNormalizer.isValidPlateFormat("53N-4567"))
        assertTrue(LicensePlateNormalizer.isValidPlateFormat("50E78427"))
        assertTrue(LicensePlateNormalizer.isValidPlateFormat("60B01234"))

        assertFalse(LicensePlateNormalizer.isValidPlateFormat("12345"))
        assertFalse(LicensePlateNormalizer.isValidPlateFormat("INVALID"))
        assertFalse(LicensePlateNormalizer.isValidPlateFormat(null))
    }

    @Test
    fun formatPlate_formatsStandard5And4DigitPlates() {
        assertEquals("50E-784.27", LicensePlateNormalizer.formatPlate("50E78427"))
        assertEquals("51B-123.45", LicensePlateNormalizer.formatPlate("51B-12345"))
        assertEquals("53N-4567", LicensePlateNormalizer.formatPlate("53N4567"))
        assertEquals("CUSTOM", LicensePlateNormalizer.formatPlate("CUSTOM"))
        assertEquals("", LicensePlateNormalizer.formatPlate(null))
    }

    @Test
    fun matchesQuery_matchesNormalizedPrefixSubstringAndNumericTail() {
        val plate = "50E-784.27"

        // Exact match
        assertTrue(LicensePlateNormalizer.matchesQuery(plate, "50E-784.27"))
        assertTrue(LicensePlateNormalizer.matchesQuery(plate, "50e78427"))

        // Prefix match
        assertTrue(LicensePlateNormalizer.matchesQuery(plate, "50E"))
        assertTrue(LicensePlateNormalizer.matchesQuery(plate, "50e"))

        // Numeric tail search
        assertTrue(LicensePlateNormalizer.matchesQuery(plate, "78427"))
        assertTrue(LicensePlateNormalizer.matchesQuery(plate, "427"))
        assertTrue(LicensePlateNormalizer.matchesQuery(plate, "784"))

        // Substring
        assertTrue(LicensePlateNormalizer.matchesQuery(plate, "E784"))

        // Non match
        assertFalse(LicensePlateNormalizer.matchesQuery(plate, "51B"))
        assertFalse(LicensePlateNormalizer.matchesQuery(plate, "99999"))
        assertFalse(LicensePlateNormalizer.matchesQuery(plate, ""))
    }
}
