package com.hcmcbustracker.core.network.api

import com.hcmcbustracker.core.network.dto.BusStopDto
import com.hcmcbustracker.core.network.dto.PredictionDto
import com.hcmcbustracker.core.network.dto.RouteComboDto
import com.hcmcbustracker.core.network.dto.RouteDetailDto
import com.hcmcbustracker.core.network.dto.RoutePathDto
import com.hcmcbustracker.core.network.dto.RouteVarDto
import com.hcmcbustracker.core.network.dto.RoutesThroughStopDto
import com.hcmcbustracker.core.network.dto.StopAutocompleteDto
import com.hcmcbustracker.core.network.dto.TimetableDto
import com.hcmcbustracker.core.network.dto.TripDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Retrofit service interface communicating directly with the official HCMC bus backend (apicms.ebms.vn).
 */
interface EbmsApiService {

    companion object {
        const val BASE_URL = "https://apicms.ebms.vn/"
    }

    /**
     * Group A: Route APIs
     */
    @GET("businfo/getroutesforcombo")
    suspend fun getRoutesForCombo(): List<RouteComboDto>

    @GET("businfo/getroutebyid/{routeId}")
    suspend fun getRouteById(
        @Path("routeId") routeId: Int
    ): RouteDetailDto

    @GET("businfo/getvarsbyroute/{routeId}")
    suspend fun getVarsByRoute(
        @Path("routeId") routeId: Int
    ): List<RouteVarDto>

    @GET("businfo/getpathsbyvar/{routeId}/{varId}")
    suspend fun getPathsByVar(
        @Path("routeId") routeId: Int,
        @Path("varId") varId: Int
    ): RoutePathDto

    @GET("businfo/gettimetablebyroute/{routeId}")
    suspend fun getTimetableByRoute(
        @Path("routeId") routeId: Int
    ): List<TimetableDto>

    @GET("businfo/gettripsbytimetable/{routeId}/{timetableId}")
    suspend fun getTripsByTimetable(
        @Path("routeId") routeId: Int,
        @Path("timetableId") timetableId: Int
    ): List<TripDto>

    /**
     * Group B: Bus Stop APIs
     */
    @GET("businfo/getstopsforautocomplete")
    suspend fun getStopsForAutocomplete(): StopAutocompleteDto

    @GET("businfo/getstopsinbounds/{minLon}/{minLat}/{maxLon}/{maxLat}")
    suspend fun getStopsInBounds(
        @Path("minLon") minLon: Double,
        @Path("minLat") minLat: Double,
        @Path("maxLon") maxLon: Double,
        @Path("maxLat") maxLat: Double
    ): List<BusStopDto>

    @GET("businfo/getstopsbyvar/{routeId}/{varId}")
    suspend fun getStopsByVar(
        @Path("routeId") routeId: Int,
        @Path("varId") varId: Int
    ): List<BusStopDto>

    @GET("businfo/getroutesthroughstop/{stopId}")
    suspend fun getRoutesThroughStop(
        @Path("stopId") stopId: Int
    ): List<RoutesThroughStopDto>

    /**
     * Group D: Prediction APIs (Real-time arrival ETA)
     */
    @GET("prediction/predictbystopid/{stopId}")
    suspend fun getPredictionByStopId(
        @Path("stopId") stopId: Int
    ): List<PredictionDto>

    @GET("prediction/predictbystopcode/{stopCode}")
    suspend fun getPredictionByStopCode(
        @Path("stopCode") stopCode: String
    ): List<PredictionDto>

    @GET("prediction/{routeId}/{varId}/{stopId}/predictnextstops/{maxNextStops}")
    suspend fun getPredictNextStops(
        @Path("routeId") routeId: Int,
        @Path("varId") varId: Int,
        @Path("stopId") stopId: Int,
        @Path("maxNextStops") maxNextStops: Int = 3
    ): List<PredictionDto>
}
