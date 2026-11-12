package com.hcmcbustracker.core.network

import com.hcmcbustracker.core.network.dto.BusStopDto
import com.hcmcbustracker.core.network.dto.StopAutocompleteDto
import com.hcmcbustracker.core.network.mapper.toDomain
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BusStopDtoParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun parseBusStopList_decodesCoordinatesAndRoutes() {
        val sampleJson = """
            [
              {
                "StopId": 23,
                "Code": "Q1 134",
                "Name": "Hồ Hảo Hớn",
                "StopType": "Trụ dừng",
                "Zone": "Quận 1",
                "Ward": null,
                "AddressNo": "301F",
                "Street": "Trần Hưng Đạo",
                "SupportDisability": "Có",
                "Status": "Đang khai thác",
                "Lng": 106.690636,
                "Lat": 10.762395,
                "Search": "HHH 301F THD",
                "Routes": "01, 139, 152, DL03"
              }
            ]
        """.trimIndent()

        val dtos: List<BusStopDto> = json.decodeFromString(sampleJson)
        assertEquals(1, dtos.size)

        val stop = dtos[0].toDomain()
        assertEquals(23, stop.id)
        assertEquals("Q1 134", stop.code)
        assertEquals("Hồ Hảo Hớn", stop.name)
        assertEquals(106.690636, stop.lng, 0.000001)
        assertEquals(10.762395, stop.lat, 0.000001)
        assertTrue(stop.supportDisability)
        assertEquals(listOf("01", "139", "152", "DL03"), stop.routes)
    }

    @Test
    fun parseStopAutocompleteDto_decodesParallelArrays() {
        val sampleJson = """
            {
              "l": ["BBD 21 TDT", "NbHH 385 CMTT"],
              "v": ["[Q1 031] Bến Bạch Đằng (21 Tôn Đức Thắng, Quận 1)", "[Q3 045] Ngã ba Hòa Hưng (385 CMTT, Quận 3)"],
              "g": [79, 385]
            }
        """.trimIndent()

        val dto: StopAutocompleteDto = json.decodeFromString(sampleJson)
        assertEquals(2, dto.l.size)
        assertEquals(2, dto.v.size)
        assertEquals(2, dto.g.size)
        assertEquals(79, dto.g[0])
        assertEquals(385, dto.g[1])
    }
}
