package com.hcmcbustracker.core.network

import com.hcmcbustracker.core.network.dto.RouteComboDto
import com.hcmcbustracker.core.network.dto.RouteDetailDto
import com.hcmcbustracker.core.network.mapper.toDomain
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RouteDtoParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun parseRouteComboList_succeedsAndMapsCorrectly() {
        val sampleJson = """
            [
              {"value":"1","lable":"[01] Ga Ba Son - Bến xe buýt Chợ Lớn","key":0},
              {"value":"3","lable":"[03] Bến Thành - Thạnh Xuân","key":0},
              {"value":"102","lable":"[65] Bến Thành - Cách Mạng Tháng Tám - Bến xe An Sương","key":0}
            ]
        """.trimIndent()

        val dtos: List<RouteComboDto> = json.decodeFromString(sampleJson)
        assertEquals(3, dtos.size)

        val r1 = dtos[0].toDomain()
        assertEquals(1, r1.id)
        assertEquals("01", r1.routeNo)
        assertEquals("Ga Ba Son - Bến xe buýt Chợ Lớn", r1.name)

        val r3 = dtos[2].toDomain()
        assertEquals(102, r3.id)
        assertEquals("65", r3.routeNo)
        assertEquals("Bến Thành - Cách Mạng Tháng Tám - Bến xe An Sương", r3.name)
    }

    @Test
    fun parseRouteDetail_succeedsAndCleansTrailingCharacters() {
        val sampleJson = """
            {
              "RouteId": 1,
              "RouteNo": "01",
              "RouteName": "Ga Ba Son - Bến xe buýt Chợ Lớn",
              "Color": "#FFB07C",
              "Type": "Phổ thông - Có trợ giá",
              "Distance": 9620.0,
              "Orgs": "Công ty Cổ phần Xe khách Phương Trang FutaBusLines, ĐT: 1900638494<br/>",
              "TimeOfTrip": "40",
              "Headway": "8 - 20",
              "OperationTime": "05:00 - 22:00",
              "NumOfSeats": "40",
              "OutBoundName": "Bến xe buýt Chợ Lớn¿",
              "InBoundName": "Ga Ba Son¿",
              "OutBoundDescription": "Đường Nguyễn Siêu - Ngô Văn Năm¿",
              "InBoundDescription": "Bến xe buýt Chợ Lớn - Lê Quang Sung¿",
              "TotalTrip": "216 [TPD]",
              "Tickets": "<br/>- Vé lượt: 5,000 VNĐ"
            }
        """.trimIndent()

        val dto: RouteDetailDto = json.decodeFromString(sampleJson)
        val domain = dto.toDomain()

        assertEquals(1, domain.id)
        assertEquals("01", domain.routeNo)
        assertEquals("#FFB07C", domain.color)
        assertEquals(9620.0, domain.distanceMeters ?: 0.0, 0.001)
        assertEquals("Đường Nguyễn Siêu - Ngô Văn Năm", domain.outBoundDesc)
        assertEquals("Bến xe buýt Chợ Lớn - Lê Quang Sung", domain.inBoundDesc)
        assertNotNull(domain.orgs)
    }
}
