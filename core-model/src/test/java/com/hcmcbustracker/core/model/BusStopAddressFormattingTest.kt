package com.hcmcbustracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BusStopAddressFormattingTest {

    private fun createBusStop(
        addressNo: String,
        street: String,
        ward: String? = null,
        zone: String = ""
    ) = BusStop(
        id = 1,
        code = "Q1 001",
        name = "Trạm Test",
        stopType = "Bến xe",
        zone = zone,
        ward = ward,
        addressNo = addressNo,
        street = street,
        supportDisability = false,
        status = "Active",
        lat = 10.77,
        lng = 106.69,
        searchKeywords = "",
        routes = emptyList()
    )

    @Test
    fun `formatStopAddress formats complete standard address correctly`() {
        val stop = createBusStop(
            addressNo = "4",
            street = "Hàm Nghi",
            ward = "Phường Bến Thành",
            zone = "Quận 1"
        )
        assertEquals("4, Hàm Nghi, Phường Bến Thành, Quận 1", stop.fullAddress)
    }

    @Test
    fun `formatStopAddress trims trailing comma from addressNo`() {
        val stop = createBusStop(
            addressNo = "127-129, Đồng Khởi, Phường Bến Nghé,",
            street = "Đồng Khởi",
            ward = null,
            zone = ""
        )
        assertEquals("127-129, Đồng Khởi, Phường Bến Nghé", stop.fullAddress)
    }

    @Test
    fun `formatStopAddress trims dangling incomplete administrative qualifiers`() {
        val stop = createBusStop(
            addressNo = "Đối diện The Nexus, Nguyễn Siêu, Phường",
            street = "Nguyễn Siêu",
            ward = null,
            zone = ""
        )
        assertEquals("Đối diện The Nexus, Nguyễn Siêu", stop.fullAddress)
    }

    @Test
    fun `formatStopAddress deduplicates street when contained in addressNo`() {
        val stop = createBusStop(
            addressNo = "150 Trần Hưng Đạo",
            street = "Trần Hưng Đạo",
            ward = "Phường Nguyễn Cư Trinh",
            zone = "Quận 1"
        )
        assertEquals("150 Trần Hưng Đạo, Phường Nguyễn Cư Trinh, Quận 1", stop.fullAddress)
    }

    @Test
    fun `formatStopAddress cleans multiple commas and excessive delimiters`() {
        val stop = createBusStop(
            addressNo = "Số 10, ",
            street = "Lê Lợi, ",
            ward = "Phường Bến Nghé, ",
            zone = "Quận 1, "
        )
        assertEquals("Số 10, Lê Lợi, Phường Bến Nghé, Quận 1", stop.fullAddress)
    }

    @Test
    fun `formatStopAddress handles empty or blank fields cleanly`() {
        val stop = createBusStop(
            addressNo = "",
            street = "Nguyễn Huệ",
            ward = null,
            zone = "Quận 1"
        )
        assertEquals("Nguyễn Huệ, Quận 1", stop.fullAddress)
    }

    @Test
    fun `formatStopAddress returns empty string when all parts are blank or only dangling words`() {
        val stop = createBusStop(
            addressNo = "Phường",
            street = "",
            ward = null,
            zone = ""
        )
        assertEquals("", stop.fullAddress)
    }
}
