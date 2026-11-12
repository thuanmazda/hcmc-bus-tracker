package com.hcmcbustracker.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hcmcbustracker.core.database.dao.BusStopDao
import com.hcmcbustracker.core.database.dao.RouteDao
import com.hcmcbustracker.core.database.dao.RoutePathDao
import com.hcmcbustracker.core.database.entity.BusStopEntity
import com.hcmcbustracker.core.database.entity.RouteEntity
import com.hcmcbustracker.core.database.entity.RoutePathEntity
import com.hcmcbustracker.core.database.entity.RouteStopCrossReference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RoomDaoTest {

    private lateinit var database: BusTrackerDatabase
    private lateinit var routeDao: RouteDao
    private lateinit var busStopDao: BusStopDao
    private lateinit var routePathDao: RoutePathDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BusTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        routeDao = database.routeDao()
        busStopDao = database.busStopDao()
        routePathDao = database.routePathDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndQueryRoutes_succeedsWithCorrectSorting() = runTest {
        val r1 = RouteEntity(id = 1, routeNo = "01", name = "Ga Ba Son - Bến xe buýt Chợ Lớn", normalizedSearch = "01 ga ba son ben xe buyt cho lon")
        val r2 = RouteEntity(id = 13, routeNo = "102", name = "Bến xe buýt Sài Gòn - Bến xe Miền Tây", normalizedSearch = "102 ben xe buyt sai gon ben xe mien tay")
        val r3 = RouteEntity(id = 3, routeNo = "03", name = "Bến Thành - Thạnh Xuân", normalizedSearch = "03 ben thanh thanh xuan")

        routeDao.insertRoutes(listOf(r1, r2, r3))

        val all = routeDao.getAllRoutes().first()
        assertEquals(3, all.size)
        // Numeric sort: 01, 03, 102
        assertEquals("01", all[0].routeNo)
        assertEquals("03", all[1].routeNo)
        assertEquals("102", all[2].routeNo)
    }

    @Test
    fun searchRoutes_matchesBothNumberAndNormalizedName() = runTest {
        val r1 = RouteEntity(id = 1, routeNo = "01", name = "Ga Ba Son - Bến xe buýt Chợ Lớn", normalizedSearch = "01 ga ba son ben xe buyt cho lon")
        val r2 = RouteEntity(id = 13, routeNo = "102", name = "Bến xe buýt Sài Gòn - Bến xe Miền Tây", normalizedSearch = "102 ben xe buyt sai gon ben xe mien tay")
        routeDao.insertRoutes(listOf(r1, r2))

        val searchByNo = routeDao.searchRoutes("01").first()
        assertEquals(1, searchByNo.size)
        assertEquals("01", searchByNo[0].routeNo)

        val searchByName = routeDao.searchRoutes("cho lon").first()
        assertEquals(1, searchByName.size)
        assertEquals(1, searchByName[0].id)
    }

    @Test
    fun insertAndQueryStops_searchAndBoundsWorkAccurately() = runTest {
        val s1 = BusStopEntity(
            id = 4223,
            code = "Q1 102",
            name = "Trạm Trung chuyển trên đường Hàm Nghi",
            stopType = "Nhà chờ",
            zone = "Quận 1",
            ward = "Phường Bến Thành",
            addressNo = "Hàm Nghi 4",
            street = "Hàm Nghi",
            supportDisability = true,
            status = "Đang khai thác",
            lat = 10.771043,
            lng = 106.699514,
            searchKeywords = "TTctdHN HN4 HN",
            routesRaw = "01, 102, 44, 65, 86",
            normalizedSearch = "q1 102 tram trung chuyen tren duong ham nghi ham nghi quan 1"
        )
        busStopDao.insertStops(listOf(s1))

        val stop = busStopDao.getStopById(4223).first()
        assertNotNull(stop)
        assertEquals("Q1 102", stop?.code)

        // Test Bounding box
        val inBounds = busStopDao.getStopsInBounds(10.76, 10.78, 106.69, 106.71).first()
        assertEquals(1, inBounds.size)

        // Test Search
        val searched = busStopDao.searchStops("ham nghi").first()
        assertEquals(1, searched.size)
        assertEquals(4223, searched[0].id)
    }

    @Test
    fun routeStopCrossReference_preservesStopOrder() = runTest {
        val s1 = BusStopEntity(id = 10, code = "S1", name = "Stop 1", stopType = "", zone = "", ward = null, addressNo = "", street = "", supportDisability = false, status = "", lat = 10.0, lng = 106.0, searchKeywords = "", routesRaw = "", normalizedSearch = "")
        val s2 = BusStopEntity(id = 20, code = "S2", name = "Stop 2", stopType = "", zone = "", ward = null, addressNo = "", street = "", supportDisability = false, status = "", lat = 10.1, lng = 106.1, searchKeywords = "", routesRaw = "", normalizedSearch = "")
        busStopDao.insertStops(listOf(s1, s2))

        val refs = listOf(
            RouteStopCrossReference(routeId = 1, varId = 1, stopId = 20, orderIndex = 0),
            RouteStopCrossReference(routeId = 1, varId = 1, stopId = 10, orderIndex = 1)
        )
        busStopDao.replaceRouteStopRefs(1, 1, refs)

        val orderedStops = busStopDao.getStopsForRouteVar(1, 1).first()
        assertEquals(2, orderedStops.size)
        assertEquals(20, orderedStops[0].id) // First in orderIndex
        assertEquals(10, orderedStops[1].id) // Second in orderIndex
    }

    @Test
    fun routePathEntity_storesAndRetrievesPolyline() = runTest {
        val entity = RoutePathEntity(
            compositeKey = "1_1",
            routeId = 1,
            varId = 1,
            latsJson = "[10.77, 10.78]",
            lngsJson = "[106.69, 106.70]",
            pointCount = 2
        )
        routePathDao.insertPath(entity)

        val retrieved = routePathDao.getPathOnce("1_1")
        assertNotNull(retrieved)
        assertEquals(2, retrieved?.pointCount)
    }
}
