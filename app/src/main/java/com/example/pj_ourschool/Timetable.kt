package com.example.pj_ourschool

// DB import
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch



//데이터베이스에서 시간표 가져오는 코드
class Timetable : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.timetable)

        // 뒤로가기 버튼 클릭 이벤트
        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // 시간표 데이터 로드
        loadTimetableData()
    }

    private fun loadTimetableData() {
        // 코루틴을 사용하여 백그라운드에서 DB 작업 수행
        lifecycleScope.launch {
            try {
                val connection = MSSQLConnector.getConnection()
                if (connection != null) {
                    // 사용자의 학번 또는 ID 가져오기 (예: 로그인 정보에서)
                    val studentId = getStudentId()

                    // 학생의 시간표 정보 조회 쿼리
                    val query = """
                        SELECT day, period, subject_name, classroom
                        FROM timetable
                        WHERE student_id = ?
                        ORDER BY day, period
                    """

                    connection.prepareStatement(query).use { statement ->
                        statement.setString(1, studentId)

                        statement.executeQuery().use { resultSet ->
                            while (resultSet.next()) {
                                val day = resultSet.getInt("day")      // 1: 월, 2: 화, ..., 5: 금
                                val period = resultSet.getInt("period") // 1~9교시
                                val subjectName = resultSet.getString("subject_name")
                                val classroom = resultSet.getString("classroom")

                                // UI에 시간표 정보 표시
                                updateTimetableCell(day, period, subjectName, classroom)
                            }
                        }
                    }

                    connection.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // 오류 처리 (예: 토스트 메시지 표시)
                runOnUiThread {
                    // Toast.makeText(this@Timetable, "시간표 로드 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 학생 ID를 가져오는 함수 (SharedPreferences 등에서 가져올 수 있음)
    private fun getStudentId(): String {
        // 예시로 하드코딩된 값 반환, 실제로는 로그인한 사용자 정보에서 가져와야 함
        return "12345678"
    }

    // 시간표 셀 업데이트 함수
    private fun updateTimetableCell(day: Int, period: Int, subjectName: String, classroom: String) {
        // 요일과 교시에 해당하는 셀 ID 생성
        val cellId = "cell_${period}_${day}"

        // ID로 TextView 찾기
        val cellResId = resources.getIdentifier(cellId, "id", packageName)
        if (cellResId != 0) {
            val cellView = findViewById<TextView>(cellResId)

            // 셀에 과목명과 강의실 정보 표시
            runOnUiThread {
                cellView.text = "$subjectName\n$classroom"
            }
        }
    }
}
