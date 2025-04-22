package com.example.pj_ourschool.util

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pj_ourschool.R
import com.example.pj_ourschool.SchoolAdapter
import com.example.pj_ourschool.SignupInfoActivity
import com.example.pj_ourschool.MSSQLConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.SQLException
import android.view.LayoutInflater

class SignupActivity : AppCompatActivity() {

    private lateinit var editSchool: EditText
    private lateinit var imgSearch: ImageView
    private lateinit var btnNext: Button
    private lateinit var recyclerViewSchools: RecyclerView
    private lateinit var schoolAdapter: SchoolAdapter
    private val schoolList = mutableListOf<String>()
    private var selectedSchoolName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup1)

        editSchool = findViewById(R.id.editSchool)
        imgSearch = findViewById(R.id.imgSearch)
        btnNext = findViewById(R.id.btnNext)
        recyclerViewSchools = findViewById(R.id.recyclerViewSchools)

        schoolAdapter = SchoolAdapter(schoolList,
            onItemClick = { schoolName ->
                selectedSchoolName = schoolName
                btnNext.isEnabled = true
                btnNext.text = "다음"
            },
            onSchoolSelected = { schoolName ->
                editSchool.setText(schoolName) // 클릭 시 검색창 텍스트 변경
            }
        )

        recyclerViewSchools.layoutManager = LinearLayoutManager(this)
        recyclerViewSchools.adapter = schoolAdapter
        recyclerViewSchools.visibility = View.GONE

        imgSearch.setOnClickListener {
            val schoolName = editSchool.text.toString().trim()
            if (schoolName.isNotEmpty()) {
                // 검색 시 선택 상태 초기화
                schoolAdapter.clearSelection()
                selectedSchoolName = null
                btnNext.isEnabled = false
                btnNext.text = "다음"
                searchSchools(schoolName)
            } else {
                schoolList.clear()
                schoolAdapter.notifyDataSetChanged()
                recyclerViewSchools.visibility = View.GONE
                btnNext.isEnabled = false
                btnNext.text = "다음"
                Toast.makeText(this, "학교명을 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        btnNext.setOnClickListener {
            selectedSchoolName?.let { schoolName ->
                showSchoolConfirmDialog(schoolName)
            } ?: run {
                Toast.makeText(this, "학교를 선택해주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        btnNext.isEnabled = false
        btnNext.text = "다음"
    }

    private fun searchSchools(searchText: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val connection = MSSQLConnector.getConnection()

            if (connection != null) {
                try {
                    val statement = connection.createStatement()
                    val query = "SELECT school_name FROM school WHERE school_name LIKE '%${searchText}%';"
                    val resultSet = statement.executeQuery(query)

                    schoolList.clear()
                    while (resultSet.next()) {
                        val foundSchool = resultSet.getString("school_name")
                        schoolList.add(foundSchool)
                    }

                    withContext(Dispatchers.Main) {
                        schoolAdapter.notifyDataSetChanged()
                        recyclerViewSchools.visibility = if (schoolList.isNotEmpty()) View.VISIBLE else View.GONE
                        if (schoolList.isEmpty()) {
                            btnNext.isEnabled = false
                            btnNext.text = "다음"
                            Toast.makeText(this@SignupActivity, "'$searchText'을 포함하는 학교를 찾을 수 없습니다.", Toast.LENGTH_LONG).show()
                        } else if (selectedSchoolName != null) {
                            btnNext.isEnabled = true
                            btnNext.text = "다음"
                        }
                    }

                    resultSet.close()
                    statement.close()
                    connection.close()

                } catch (e: SQLException) {
                    Log.e("DB Query Error", "Error searching schools: ${e.message}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SignupActivity, "데이터베이스 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SignupActivity, "데이터베이스 연결에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showSchoolConfirmDialog(schoolName: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_school_confirm, null)
        val txtMessage: TextView = dialogView.findViewById(R.id.txtConfirmMessage)
        val btnConfirm: Button = dialogView.findViewById(R.id.btnConfirm)
        val btnCancel: Button = dialogView.findViewById(R.id.btnCancel)

        txtMessage.text = "'$schoolName' 학교로 인증하시겠어요?"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            moveToNextActivity(schoolName)
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun moveToNextActivity(schoolName: String) {
        val intent = Intent(this, SignupInfoActivity::class.java)
        intent.putExtra("schoolName", schoolName)
        startActivity(intent)
    }
}