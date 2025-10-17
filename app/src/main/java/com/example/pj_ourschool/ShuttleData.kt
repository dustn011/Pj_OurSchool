// ShuttleData.kt 파일에 이 코드를 한 번만 넣으세요.

package com.example.pj_ourschool

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// 셔틀 데이터 클래스
data class Shuttle(
    val id: Int,
    val name: String,
    val dayOfWeek: String,
    val departureTime: String,
    val departureStation: String,
    val cancelDays: String?
)

// ShuttleService 객체
object ShuttleService {
    private const val TAG = "ShuttleService"

    // DB에서 스케줄을 가져오는 함수 (MainActivity.kt에서 가져온 버전)
    suspend fun getShuttleSchedulesFromDb(): List<Shuttle> = withContext(Dispatchers.IO) {
        val schedules = mutableListOf<Shuttle>()
        var connection: Connection? = null
        var resultSet: ResultSet? = null
        var statement: PreparedStatement? = null

        try {
            connection = MSSQLConnector.getConnection() // 외부 MSSQLConnector 사용
            if (connection != null) {
                val query = """
                    SELECT b.id, b.route_segment, CONVERT(VARCHAR(5), b.dispatch_time, 108) AS dispatch_time_hm, b.bus_type, b.service_date
                    FROM bus_info AS b
                    ORDER BY b.dispatch_time ASC
                """.trimIndent()
                statement = connection.prepareStatement(query)
                resultSet = statement.executeQuery()

                while (resultSet.next()) {
                    val id = resultSet.getInt("id")
                    val routeSegmentInt = resultSet.getInt("route_segment")
                    val departureTime = resultSet.getString("dispatch_time_hm")
                    val busName = resultSet.getString("bus_type")
                    val serviceDate = resultSet.getString("service_date")

                    val departureStation = if (routeSegmentInt == 1) {
                        "정문"
                    } else {
                        "기숙사"
                    }

                    schedules.add(Shuttle(id, busName, "평일", departureTime, departureStation, serviceDate))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "셔틀 스케줄 로드 중 오류 발생: ${e.message}", e)
        } finally {
            try { resultSet?.close() } catch (e: Exception) { /* ignore */ }
            try { statement?.close() } catch (e: Exception) { /* ignore */ }
            try { connection?.close() } catch (e: Exception) { /* ignore */ }
        }
        schedules
    }

    // 주말 여부 확인 함수
    fun isHoliday(): Boolean {
        val today = Calendar.getInstance(Locale.KOREA)
        val dayOfWeekInt = today.get(Calendar.DAY_OF_WEEK)
        return dayOfWeekInt == Calendar.SATURDAY || dayOfWeekInt == Calendar.SUNDAY
    }

    // 셔틀 결행 여부 확인 함수
    fun isShuttleCanceledToday(shuttle: Shuttle): Boolean {
        if (shuttle.cancelDays.isNullOrEmpty() || shuttle.cancelDays.equals("NULL", ignoreCase = true)) {
            return false
        }
        if (isHoliday()) {
            return true
        }

        val currentDayOfWeek = SimpleDateFormat("E", Locale.KOREA).format(Calendar.getInstance().time)
        val canceledDays = shuttle.cancelDays.split(",").map { it.trim() }
        return canceledDays.contains(currentDayOfWeek)
    }
}