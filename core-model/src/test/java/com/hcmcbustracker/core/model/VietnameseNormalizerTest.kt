package com.hcmcbustracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VietnameseNormalizerTest {

    @Test
    fun removeAccents_correctlyRemovesAllVietnameseMarks() {
        assertEquals("ben xe buyt cho lon", VietnameseNormalizer.removeAccents("Bến xe buýt Chợ Lớn"))
        assertEquals("ga ba son", VietnameseNormalizer.removeAccents("Ga Ba Son"))
        assertEquals("tram trung chuyen tren duong ham nghi", VietnameseNormalizer.removeAccents("Trạm Trung chuyển trên đường Hàm Nghi"))
        assertEquals("dinh tien hoang", VietnameseNormalizer.removeAccents("Đinh Tiên Hoàng"))
        assertEquals("dinh doc lap", VietnameseNormalizer.removeAccents("Dinh Độc Lập"))
    }

    @Test
    fun removeAccents_handlesMultipleSpacesAndTrimming() {
        assertEquals("ben thanh", VietnameseNormalizer.removeAccents("   Bến    Thành   "))
    }

    @Test
    fun containsIgnoreCaseAndAccents_matchesAppropriately() {
        assertTrue(VietnameseNormalizer.containsIgnoreCaseAndAccents("Bến xe Miền Tây", "mien tay"))
        assertTrue(VietnameseNormalizer.containsIgnoreCaseAndAccents("Trần Hưng Đạo", "hung dao"))
        assertTrue(VietnameseNormalizer.containsIgnoreCaseAndAccents("Chợ Bến Thành", "BEN THANH"))
        assertFalse(VietnameseNormalizer.containsIgnoreCaseAndAccents("Nguyễn Văn Linh", "Nguyen Thi"))
    }

    @Test
    fun matchesAllTokens_supportsOutOfOrderKeywords() {
        assertTrue(VietnameseNormalizer.matchesAllTokens("[01] Ga Ba Son - Bến xe buýt Chợ Lớn", "01 cho lon"))
        assertTrue(VietnameseNormalizer.matchesAllTokens("[102] Bến xe buýt Sài Gòn - Bến xe Miền Tây", "mien tay 102 sai gon"))
        assertFalse(VietnameseNormalizer.matchesAllTokens("[01] Ga Ba Son", "02 ba son"))
    }
}
