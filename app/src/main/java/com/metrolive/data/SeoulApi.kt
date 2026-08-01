package com.metrolive.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.metrolive.ApiKeys
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

/**
 * 서울 열린데이터광장 실시간 지하철 API
 * http://swopenapi.seoul.go.kr/api/subway/{KEY}/json/{서비스}/{start}/{end}/{파라미터}
 */
interface SeoulApi {

    @GET("api/subway/{key}/json/realtimePosition/0/100/{line}")
    suspend fun realtimePosition(
        @Path("key") key: String = ApiKeys.current(),
        @Path("line") lineName: String,          // 예: "2호선"
    ): RealtimePositionResponse

    @GET("api/subway/{key}/json/realtimeStationArrival/0/30/{station}")
    suspend fun realtimeArrival(
        @Path("key") key: String = ApiKeys.current(),
        @Path("station") stationName: String,    // 예: "시청"
    ): RealtimeArrivalResponse

    companion object {
        fun create(): SeoulApi {
            val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
            val client = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl("http://swopenapi.seoul.go.kr/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(SeoulApi::class.java)
        }
    }
}
