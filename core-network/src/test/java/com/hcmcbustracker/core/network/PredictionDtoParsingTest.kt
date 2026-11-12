package com.hcmcbustracker.core.network

import com.hcmcbustracker.core.network.dto.PredictionDto
import com.hcmcbustracker.core.network.mapper.toDomain
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PredictionDtoParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun parsePredictionPayload_realDataDecodesAccurately() {
        val sampleJson = """
            [
              {
                "arrs": [
                  {
                    "d": 1168.2146370208116,
                    "dts": "2026-11-09T21:13:29+07:00",
                    "s": 24.0,
                    "sts": 0,
                    "t": 250,
                    "v": "50E20651"
                  },
                  {
                    "d": 8561.88450933371,
                    "dts": "2026-11-09T21:13:23+07:00",
                    "s": 23.0,
                    "sts": 0,
                    "t": 1791,
                    "v": "50E20658"
                  }
                ],
                "r": 65,
                "rN": "Cảng Quận 4 - Bình Quới",
                "rNo": "44",
                "s": 4223,
                "sN": "Trạm Trung chuyển trên đường Hàm Nghi",
                "v": 130,
                "vN": "Cảng Quận 4"
              }
            ]
        """.trimIndent()

        val dtos: List<PredictionDto> = json.decodeFromString(sampleJson)
        assertEquals(1, dtos.size)

        val pred = dtos[0].toDomain()
        assertEquals(65, pred.routeId)
        assertEquals("44", pred.routeNo)
        assertEquals(4223, pred.stopId)
        assertEquals(130, pred.varId)
        assertEquals(2, pred.vehicles.size)

        val v1 = pred.vehicles[0]
        assertEquals("50E20651", v1.plateNumber)
        assertEquals("50E-206.51", v1.formattedPlate)
        assertEquals(250, v1.etaSeconds)
        assertEquals(5, v1.etaMinutes)
        assertEquals(24.0, v1.speedKmH, 0.01)
        assertEquals("1.2 km", v1.formattedDistance)
    }
}
