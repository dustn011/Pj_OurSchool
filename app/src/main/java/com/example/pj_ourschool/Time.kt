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
    private lateinit var noticeTextView: TextView

    // 9개의 색상 미리 정의
    private val classColors = arrayOf(
        Color.parseColor("#FFE5FA"), // 아주 연한 핑크색
        Color.parseColor("#D6F3FF"), // 아주 연한 하늘색
        Color.parseColor("#EBFADC"), // 아주 연한 녹색
        Color.parseColor("#FDFDE7"), // 아주 연한 노란색
        Color.parseColor("#E8FFF2"), // 아주 연한 민트색
        Color.parseColor("#FFDDDD"), // 아주 연한 빨간색
        Color.parseColor("#E5DDFF"), // 아주 연한 보라색
        Color.parseColor("#EFEFEF")  // 아주 연한 회색
    )

    // 과목명을 색상 인덱스에 매핑하는 맵
    private val classColorMap = mutableMapOf<String, Int>()
    private var colorIndex = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_time_table)

        // SharedPreferences에서 아이디를 읽어옵니다.
        val sharedPref = getSharedPreferences("user_info", Context.MODE_PRIVATE)
        val userId = sharedPref.getString("userId", "user123") // 기본값 설정 가능

        // UI 요소 초기화
        val leftArrow: ImageButton = findViewById(R.id.left_arrow)
        val profileImageView: ImageButton = findViewById(R.id.Profile)
        val homeImageView: ImageView = findViewById(R.id.home)
        val campusImageView: ImageView = findViewById(R.id.campus)
        val busImageView: ImageView = findViewById(R.id.bus)
        val chatImageView: ImageView = findViewById(R.id.chat)
        timetableGrid = findViewById(R.id.timetableGrid)
        noticeTextView = findViewById(R.id.noticeText) // 사이버 강의 목록을 표시할 TextView 초기화

        // cells 배열 초기화
        cells = Array(9) { row ->
            Array(5) { col ->
                val cellId =
                    resources.getIdentifier("cell_${row + 1}_${col + 1}", "id", packageName)
                findViewById(cellId)
            }
        }

        // 모든 셀 초기화 및 클릭 리스너 설정
        for (i in 0..8) {
            for (j in 0..4) {
                val cell = cells[i][j]
                cell.setTextColor(Color.BLACK)
                cell.text = ""
                cell.setOnClickListener { view ->
                    val textView = view as TextView
                    val cellText = textView.text.toString()
                    val lines = cellText.split("\n")
                    if (lines.size >= 2) {
                        val classroomInfo = lines.drop(1).joinToString(" ")
                        // "건물번호-호수,건물번호-호수" 형식에서 첫 번째 건물 번호 추출
                        val buildingNumber = classroomInfo.split(",").firstOrNull()?.split("-")?.firstOrNull()?.trim()

                        if (!buildingNumber.isNullOrEmpty()) {
                            val intent = Intent(this, Campus::class.java)
                            intent.putExtra("buildingNumber", buildingNumber) // 추출한 건물 번호 전달
                            startActivity(intent)
                        }
                    }
                }
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
    //실제 db연동하는 코드
    private fun loadTimetableData() {
        lifecycleScope.launch {
            val timetableData = fetchTimetableDataFromMSSQL()
            updateTimetableUI(timetableData)
        }
    }


    private suspend fun fetchTimetableDataFromMSSQL(): List<Map<String, String?>> =
        withContext(Dispatchers.IO) {
            val resultList = mutableListOf<Map<String, String?>>() // 여기를 수정
            val connection = MSSQLConnector.getConnection()
            val sharedPref = getSharedPreferences("user_info", Context.MODE_PRIVATE)
            val userId = sharedPref.getString("userId", "")

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
                        val rowData = mutableMapOf<String, String?>() // 여기를 수정
                        for (i in 1..columnCount) {
                            val columnName = metaData.getColumnName(i)
                            val columnValue = resultSet.getString(i)
                            rowData[columnName] = columnValue
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


    private fun updateTimetableUI(results: List<Map<String, String?>>) {
        val dayMap = mapOf("월" to 0, "화" to 1, "수" to 2, "목" to 3, "금" to 4)
        val scheduleEntryPattern = Regex("([월화수목금])\\(((\\d+)(,\\d+)*)\\)")
        val onlineClasses = mutableListOf<String>()
        val filledCells = Array(9) { BooleanArray(5) { false } } // 셀이 채워졌는지 추적하는 배열

        // 먼저 과목명에 색상 할당
        for (result in results) {
            val className = result["class_name"] as? String
            if (className != null && !classColorMap.containsKey(className)) {
                classColorMap[className] = colorIndex % classColors.size
                colorIndex++
            }
        }

        for (result in results) {
            val className = result["class_name"] as? String ?: continue
            val classroom = result["classroom"] as? String ?: ""
            val classSchedule = result["class_schedule"] as? String ?: ""

            if (classroom.isNullOrBlank() || classSchedule.isNullOrBlank()) {
                onlineClasses.add(className)
                Log.d("TimeTable", "사이버 강의 발견 (Blank 값): $className")
                continue // 사이버 강의는 시간표 셀에 표시하지 않으므로 건너뜀
            }

            val colorIndex = classColorMap[className] ?: 0
            val cellColor = classColors[colorIndex]

            val scheduleEntries = scheduleEntryPattern.findAll(classSchedule)
            Log.d("TimeTable Parsing", "과목: $className, 전체 스케줄: $classSchedule (총 ${scheduleEntries.count()}개 항목)")

            for (match in scheduleEntries) {
                val day = match.groupValues[1]
                val timeStr = match.groupValues[2]
                val times = timeStr.split(",").mapNotNull { it.toIntOrNull() }.sorted() // 시간 순으로 정렬
                val col = dayMap[day] ?: continue

                for (i in times.indices) {
                    val time = times[i]
                    if (time in 1..9) {
                        val row = time - 1
                        if (row in 0..8 && !filledCells[row][col]) {
                            val cell = cells[row][col]
                            cell.text = className
                            if (classroom.isNotEmpty()) {
                                val parts = classroom.split(" ") // 공백을 기준으로 분리
                                val formattedClassroom = if (parts.size >= 2) {
                                    "${parts[0]}\n${parts.subList(1, parts.size).joinToString(" ")}"
                                } else {
                                    classroom
                                }
                                cell.text = "${className}\n${formattedClassroom}"
                            }
                            cell.setBackgroundColor(cellColor)
                            filledCells[row][col] = true

                            // 다음 시간대가 있고, 연속된 수업이라면 다음 칸도 색칠
                            if (i + 1 < times.size && times[i + 1] == time + 1 && row + 1 < 9 && !filledCells[row + 1][col]) {
                                cells[row + 1][col].setBackgroundColor(cellColor)
                                filledCells[row + 1][col] = true
                            }
                        }
                    }
                }
            }
        }

        // 사이버 강의 목록을 TextView에 표시
        if (onlineClasses.isNotEmpty()) {
            noticeTextView.text = "사이버강의: ${onlineClasses.joinToString(", ")}"
        } else {
            noticeTextView.text = "사이버강의: 없음"
        }
    }
}
private fun calculateBrightness(color: Int): Int {
    val r = Color.red(color)
    val g = Color.green(color)
    val b = Color.blue(color)
    // 인간의 눈은 초록색에 더 민감하고 파란색에 덜 민감하다는 공식
    return (r * 0.299 + g * 0.587 + b * 0.114).toInt()
}