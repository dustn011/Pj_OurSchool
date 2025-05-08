package com.example.pj_ourschool

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.ResultSet

class Time : AppCompatActivity() {

    private lateinit var cells: Array<Array<TextView>>
    private lateinit var timetableGrid: GridLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_time_table)

        // UI 요소 초기화
        val leftArrow: ImageButton = findViewById(R.id.left_arrow)
        val profileImageView: ImageButton = findViewById(R.id.Profile)
        val homeImageView: ImageView = findViewById(R.id.home)
        val campusImageView: ImageView = findViewById(R.id.campus)
        val busImageView: ImageView = findViewById(R.id.bus)
        val chatImageView: ImageView = findViewById(R.id.chat)
        timetableGrid = findViewById(R.id.timetableGrid)

        // cells 배열 초기화
        cells = Array(9) { row ->
            Array(5) { col ->
                val cellId =
                    resources.getIdentifier("cell_${row + 1}_${col + 1}", "id", packageName)
                findViewById(cellId)
            }
        }

        // 모든 셀 초기화
        for (i in 0..8) {
            for (j in 0..4) {
                cells[i][j].setTextColor(Color.BLACK)
                cells[i][j].text = ""
            }
        }

        // 시간표 데이터 불러와서 UI 업데이트
        loadTimetableData()

        // 클릭 리스너 설정 (기존 코드와 동일)
        leftArrow.setOnClickListener { finish() }
        profileImageView.setOnClickListener {
            val intent = Intent(this, Profile::class.java)
            startActivity(intent)
        }
        homeImageView.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
        campusImageView.setOnClickListener {
            val intent = Intent(this, Campus::class.java)
            startActivity(intent)
        }
        busImageView.setOnClickListener {
            val intent = Intent(this, ShuttleBus::class.java)
            startActivity(intent)
        }
        chatImageView.setOnClickListener {
            val intent = Intent(this, Chat::class.java)
            startActivity(intent)
        }
    }

    private fun loadTimetableData() {
        lifecycleScope.launch {
            val timetableData = fetchTimetableDataFromMSSQL()
            updateTimetableUI(timetableData)
        }
    }

    private suspend fun fetchTimetableDataFromMSSQL(): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<Map<String, Any>>()
        val connection = MSSQLConnector.getConnection()

        // SharedPreferences에서 아이디를 읽어옵니다.
        val sharedPref = getSharedPreferences("user_info", Context.MODE_PRIVATE)
        val userId = sharedPref.getString("userId","") // 기본값 설정 가능

        try {
            if (connection != null) {
                val query = """
                SELECT
                    ci.class_name,
                    ci.classroom,
                    ci.class_schedule
                FROM student_schedule AS ss
                JOIN class_info AS ci
                  ON ss.class_code = ci.class_code
                 AND ss.class_section = ci.class_section
                WHERE ss.student_id = '${userId}';
            """.trimIndent()
                val statement = connection.createStatement()
                val resultSet: ResultSet = statement.executeQuery(query)

                val metaData = resultSet.metaData
                val columnCount = metaData.columnCount

                while (resultSet.next()) {
                    val rowData = mutableMapOf<String, Any>()
                    for (i in 1..columnCount) {
                        val columnName = metaData.getColumnName(i)
                        val columnValue = resultSet.getString(i)
                        rowData[columnName] = columnValue
                        Log.d("MSSQL Data", "$columnName: $columnValue") // 로그캣 출력
                    }
                    resultList.add(rowData)
                }
                resultSet.close()
                statement.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection?.close()
        }
        resultList
    }

    private fun updateTimetableUI(results: List<Map<String, Any>>) {
        val dayMap = mapOf("월" to 0, "화" to 1, "수" to 2, "목" to 3, "금" to 4)
        val scheduleEntryPattern = Regex("([월화수목금])\\(((\\d+)(,\\d+)*)\\)")

        for (result in results) {
            val className = result["class_name"] as? String
            val classroom = result["classroom"] as? String
            val classSchedule = result["class_schedule"] as? String

            if (className != null && classroom != null && classSchedule != null) {
                val scheduleEntries = scheduleEntryPattern.findAll(classSchedule)
                Log.d("TimeTable Parsing", "과목: $className, 전체 스케줄: $classSchedule (총 ${scheduleEntries.count()}개 항목)")
                for (match in scheduleEntries) {
                    val day = match.groupValues[1]
                    val timeStr = match.groupValues[2]
                    val times = timeStr.split(",").mapNotNull { it.toIntOrNull() }
                    Log.d("TimeTable Parsed", "  - 요일: $day, 교시: $times")
                    val col = dayMap[day]
                    if (col != null) {
                        for (time in times) {
                            if (time in 1..9) {
                                val row = time - 1
                                if (row in 0..8 && col in 0..4) {
                                    val cell = cells[row][col]
                                    if (cell.text.isNullOrEmpty()) {
                                        cell.text = className
                                        if (classroom.isNotEmpty()) {
                                            cell.text = "${className}\n(${classroom})"
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Log.w("TimeTable Parsing", "요일 매핑 실패: $day")
                    }
                }
            }
        }
    }
}