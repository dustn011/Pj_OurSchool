package com.example.pj_ourschool

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.LinearLayout // LinearLayout import 추가
import android.widget.RelativeLayout // RelativeLayout import 추가 (MapView의 부모 레이아웃 파라미터 타입)
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.pj_ourschool.databinding.ActivityCampusBinding
import com.google.android.gms.location.*
import com.kakao.sdk.common.util.Utility
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.KakaoMapSdk
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelLayer
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import android.widget.Toast
import de.hdodenhof.circleimageview.CircleImageView
import android.content.Intent
import android.net.Uri
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException


class Campus : AppCompatActivity() {

    private lateinit var bindingCampus: ActivityCampusBinding
    private lateinit var mapView: MapView
    private var kakaoMap: KakaoMap? = null
    private var labelLayer: LabelLayer? = null
    private var currentMarker: Label? = null
    private var currentLocationMarker: Label? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1000
    private val REST_API_KEY = "2cb1433a2459d08c0fb7c0351fddd03d" // 여기에 실제 REST API 키를 넣어주세요.
    private var currentLocationForNavigation: Location? = null

    // 프로필 이미지 관련 상수 추가
    private val PROFILE_IMAGE_PREF = "profile_image_pref"
    private val KEY_PROFILE_IMAGE_URI = "profile_image_uri"

    // profileImageView 타입을 CircleImageView로 변경
    private lateinit var profileImageView: CircleImageView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindingCampus = ActivityCampusBinding.inflate(layoutInflater)
        setContentView(bindingCampus.root)

        val keyHash = Utility.getKeyHash(this)
        Log.d("Hash", keyHash)

        KakaoMapSdk.init(this, "8657f921e8595e3efa4a2e0663545bbe") // 여기에 실제 앱 키를 넣어주세요.

        mapView = bindingCampus.mapView // ⭐️ MapView 초기화 유지 (필수)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // ⭐️⭐️⭐️ 하단 메뉴 동적 제어 로직 시작 ⭐️⭐️⭐️
        val bottomMenu = bindingCampus.bottomMenu
        val hideMenu = intent.getBooleanExtra("hideBottomMenu", false)

        if (hideMenu) {
            // 챗봇에서 "위치 보기"를 통해 진입한 경우: 하단 메뉴 숨김
            bottomMenu.visibility = View.GONE

            // MapView의 레이아웃 파라미터 수정: bottom_menu 위에 위치하는 대신, RelativeLayout의 하단에 맞춤
            val mapParams = mapView.layoutParams as RelativeLayout.LayoutParams
            mapParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
            mapParams.removeRule(RelativeLayout.ABOVE) // 기존 bottom_menu와의 상대적인 위치 관계를 제거
            mapView.layoutParams = mapParams
            Log.d("CampusLayout", "챗봇 진입: 하단 메뉴 숨김, 맵 확장")

        } else {
            // 일반적인 경로로 진입한 경우: 하단 메뉴 표시 유지
            bottomMenu.visibility = View.VISIBLE
            // MapView는 XML에 정의된 대로 bottom_menu 위에 위치하도록 유지
            Log.d("CampusLayout", "일반 진입: 하단 메뉴 표시, 맵 유지")

            // MapView가 bottom_menu 위에 위치하도록 명시적으로 설정 (안전성 확보)
            val mapParams = mapView.layoutParams as RelativeLayout.LayoutParams
            mapParams.addRule(RelativeLayout.ABOVE, R.id.bottom_menu)
            mapParams.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            mapView.layoutParams = mapParams
        }
        // ⭐️⭐️⭐️ 하단 메뉴 동적 제어 로직 끝 ⭐️⭐️⭐️

        // UI 요소 초기화 (Binding 사용으로 변경 가능하나, 기존 코드와 일치하도록 유지)
        val leftArrow: ImageView = findViewById(R.id.left_arrow)
        profileImageView = findViewById(R.id.Profile) // CircleImageView 타입으로 초기화

        // 맵 뷰 시작
        mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {
                Log.d("KakaoMap", "onMapDestroy")
            }

            override fun onMapError(e: Exception?) {
                Log.e("KakaoMap", "onMapError", e)
            }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(map: KakaoMap) {
                kakaoMap = map
                labelLayer = map.labelManager?.layer

                // 위치 권한 확인 및 요청
                if (ContextCompat.checkSelfPermission(this@Campus, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    getCurrentLocationForNavigation() // 내비 출발지 설정을 위한 현재 위치 가져오기

                    // Chat 액티비티에서 전달된 건물 번호 확인 및 처리
                    intent?.getStringExtra("buildingNumber")?.let { buildingNumber ->
                        Log.d("Campus", "Chat 액티비티로부터 받은 건물 번호: $buildingNumber")
                        findAndMoveCameraToBuilding(buildingNumber)
                    } ?: run {
                        // 전달된 건물 번호가 없으면 현재 위치 표시
                        getCurrentLocation()
                    }
                } else {
                    ActivityCompat.requestPermissions(
                        this@Campus,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                }

                // 건물 마커 추가
                addBuildingMarkers()

                // 마커 클릭 리스너
                kakaoMap?.setOnLabelClickListener { _, _, label ->
                    val clickedBuilding = buildings.find { it.second == label.tag?.toString() }
                    if (clickedBuilding != null) {
                        bindingCampus.buildingInfoLayout.visibility = View.VISIBLE
                        bindingCampus.buildingNameTextView.text = clickedBuilding.second

                        val imageName = "cju${clickedBuilding.first}"
                        val imageResource = resources.getIdentifier(imageName, "drawable", packageName)

                        if (imageResource != 0) {
                            bindingCampus.buildingImageView.setImageResource(imageResource)
                        } else {
                            bindingCampus.buildingImageView.setImageResource(R.drawable.ic_launcher_background)
                            Log.e("Campus", "Image resource not found: $imageName")
                        }

                        bindingCampus.navigateToKakaoMapButton.setOnClickListener {
                            val destinationLat = clickedBuilding.third
                            val destinationLng = clickedBuilding.fourth
                            openKakaoMapNavigation(destinationLat, destinationLng)
                            bindingCampus.buildingInfoLayout.visibility = View.GONE
                        }
                    } else {
                        bindingCampus.buildingInfoLayout.visibility = View.GONE
                    }
                    false
                }
                // 닫기 버튼 클릭 리스너 추가
                bindingCampus.closeButton.setOnClickListener {
                    bindingCampus.buildingInfoLayout.visibility = View.GONE
                }
            }
        })

        // **프로필 이미지 로드**
        loadProfileImageUri()

        // ⭐️⭐️⭐️ 하단 메뉴 및 헤더 클릭 리스너 ⭐️⭐️⭐️
        profileImageView.setOnClickListener { startActivity(Intent(this, Profile::class.java)) }
        leftArrow.setOnClickListener { finish() }

        // 하단 메뉴 리스너 (bindingCampus를 사용하여 안전하게 접근)
        bindingCampus.home.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
        bindingCampus.time.setOnClickListener { startActivity(Intent(this, Time::class.java)) }
        bindingCampus.bus.setOnClickListener { startActivity(Intent(this, ShuttleBus::class.java)) }
        bindingCampus.chat.setOnClickListener {
            // 챗봇 버튼을 눌러 Chat으로 갈 때만 기존 내용이 유지되도록 finish() 사용.
            // (Chat에서 Campus로 올 때 Chat은 Paused 상태로 유지되므로, finish()로 돌아가면 내용이 유지됨)
            finish()
        }
        // ⭐️⭐️⭐️ 리스너 수정 끝 ⭐️⭐️⭐️
    }

    override fun onResume() {
        super.onResume()
        loadProfileImageUri()
    }

    // 건물 번호로 위치를 찾아 카메라를 이동하는 함수 (기존과 동일)
    private fun findAndMoveCameraToBuilding(buildingNumber: String) {
        val targetBuilding = buildings.find { it.first == buildingNumber }
        if (targetBuilding != null) {
            val targetLatLng = LatLng.from(targetBuilding.third, targetBuilding.fourth)
            val cameraUpdate = CameraUpdateFactory.newCenterPosition(targetLatLng, 19)
            kakaoMap?.moveCamera(cameraUpdate)
            Log.d("Campus", "카메라 이동 (건물 번호): ${targetBuilding.second} (${targetBuilding.third}, ${targetBuilding.fourth})")
        } else {
            Log.w("Campus", "해당하는 건물 번호를 찾을 수 없습니다: $buildingNumber")
            Toast.makeText(this, "'$buildingNumber' 건물 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            getCurrentLocation()
        }
    }


    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        val currentLatLng = LatLng.from(it.latitude, it.longitude)
                        addCurrentLocationMarker(currentLatLng)

                        val cameraUpdate = CameraUpdateFactory.newCenterPosition(currentLatLng, 19)
                        kakaoMap?.moveCamera(cameraUpdate)
                    } ?: run {
                        Toast.makeText(this, "현재 위치를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "위치 정보 요청 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "위치 권한이 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addCurrentLocationMarker(position: LatLng) {
        labelLayer?.let {
            currentLocationMarker?.remove()

            val markerStyle = LabelStyle.from(R.drawable.map_icon_selected)
                .setAnchorPoint(0.5f, 1.0f)

            val labelOptions = LabelOptions.from(position)
                .setStyles(markerStyle)
                .setTag("currentLocation")

            currentLocationMarker = it.addLabel(labelOptions)
        }
    }

    private fun addMarker(position: LatLng) {
        labelLayer?.let {
            currentMarker?.remove()

            val markerStyle = LabelStyle.from(R.drawable.map_icon)
                .setAnchorPoint(0.5f, 1.0f)

            val labelOptions = LabelOptions.from(position)
                .setStyles(markerStyle)
                .setTag("customMarker")

            currentMarker = it.addLabel(labelOptions)
        }
    }

    private fun addBuildingMarkers() {
        val style = LabelStyle.from(R.drawable.map_icon) // 건물 마커 아이콘 (선택 사항)

        labelLayer?.let { layer ->
            for ((number, name, lat, lng) in buildings) {
                layer.addLabel(
                    LabelOptions.from(LatLng.from(lat, lng))
                        .setStyles(style)
                        .setTag(name) // Tag는 건물 이름으로 유지 (필요에 따라 번호로 변경 가능)
                )
            }
        }
    }

    private fun getAddressFromLatLng(lat: Double, lng: Double, callback: (String) -> Unit) {
        val client = OkHttpClient()
        val url = "https://dapi.kakao.com/v2/local/geo/coord2address.json?x=$lng&y=$lat"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "KakaoAK $REST_API_KEY")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("KakaoAPI", "주소 요청 실패: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let {
                    val json = JSONObject(it)
                    val documents = json.getJSONArray("documents")
                    if (documents.length() > 0) {
                        val address = documents.getJSONObject(0)
                            .getJSONObject("address")
                            .getString("address_name")

                        runOnUiThread {
                            // callback(address)
                        }
                    }
                }
            }
        })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    getCurrentLocationForNavigation()
                    getCurrentLocation()
                } else {
                    Toast.makeText(this, "위치 정보 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                }
                return
            }
            else -> {
                // 다른 권한 요청 결과는 무시
            }
        }
    }


    private fun getCurrentLocationForNavigation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        currentLocationForNavigation = it
                    } ?: run {
                        Toast.makeText(
                            this,
                            "현재 위치를 가져올 수 없습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(
                        this,
                        "위치 정보 요청 실패: ${it.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        } else {
            Toast.makeText(this, "위치 권한이 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openKakaoMapNavigation(destLat: Double, destLng: Double) {
        currentLocationForNavigation?.let { startLocation ->
            val startLat = startLocation.latitude
            val startLng = startLocation.longitude

            //카카오 맵 uri를 몰라서 웹으로 이동하는 uri를 썻습니다 혹시 쓴다면 여기를 바꾸세요.
            val uri = Uri.parse("https://map.kakao.com/link/search/$destLat,$destLng") // 도착지 좌표 검색 후 길찾기 유도

            Log.d("NavigationUri", "생성된 URI: $uri")

            val intent = Intent(Intent.ACTION_VIEW, uri)
            val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)

            val isKakaoMapCallable = resolveInfo != null
            Log.d("KakaoMapCallable", "카카오맵 호출 가능: $isKakaoMapCallable")

            if (isKakaoMapCallable) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "카카오맵 앱을 설치해주세요.", Toast.LENGTH_SHORT).show()
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=net.daum.android.map"))
                startActivity(marketIntent)
            }
        } ?: run {
            Toast.makeText(this, "현재 위치를 가져올 수 없어 길찾기를 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 건물 정보 리스트 (건물 번호 추가)
    private val buildings = listOf(
        Quadruple("01", "청석교육역사관", 36.652875779969825, 127.49349670472847),
        Quadruple("02", "대학원", 36.65219099348803, 127.4940235571869),
        Quadruple("03", "입학취업지원관", 36.6516933387964, 127.49342764963501),
        Quadruple("04", "박물관", 36.65112059087767, 127.49626739340559),
        Quadruple("05", "청석관", 36.650882110049835, 127.49510837724529),
        Quadruple("06", "융합관", 36.651623356261105, 127.49563315757024),
        Quadruple("07", "공과대학(구관)", 36.65269020356402, 127.49695128375961),
        Quadruple("08", "보건의료과학대학", 36.65103672934571, 127.49692947528708),
        Quadruple("09", "경상대학", 36.649549188593156, 127.4970932577944),
        Quadruple("10", "교수연구동", 36.64888725216073, 127.49699953825224),
        Quadruple("11", "중앙도서관", 36.652183679932016, 127.49470011990678),
        Quadruple("12", "육군학군단", 36.6556945485927, 127.49878521712864),
        Quadruple("13", "종합강의동", 36.6483053045581, 127.49663234917526),
        Quadruple("14", "공과대학 신관", 36.65212103959428, 127.49729991021296),
        Quadruple("16", "CJU학생지원관", 36.65170099203153, 127.4926672165252324),
        Quadruple("18", "금융센터", 36.65143737571066, 127.49376431798153),
        Quadruple("20", "인문사회사범대학", 36.650013778066885, 127.49643083678156),
        Quadruple("23", "PoE관", 36.65162935191857, 127.4931029185012),
        Quadruple("26", "예술대학(구관)", 36.65974216993851, 127.50002207488309),
        Quadruple("31", "예술대학(신관)", 36.66059040788177, 127.50081329601157),
        Quadruple("32", "공예관", 36.65781189046739, 127.50046815334458),
        Quadruple("35", "공군학군단", 36.65856286659573, 127.50028008093844),
        Quadruple("36", "예지관", 36.659725255030935, 127.49706640429429),
        Quadruple("39", "충의관", 36.65537712687875, 127.49819320900963),
        Quadruple("42", "새천년종합정보관", 36.653159876558725, 127.49506704121313)
    )

    // Triple 대신 사용할 데이터 클래스 정의
    data class Quadruple<out A, out B, out C, out D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

    // --- 프로필 이미지 로드 함수 추가 ---
    private fun loadProfileImageUri() {
        val sharedPref = getSharedPreferences(PROFILE_IMAGE_PREF, Context.MODE_PRIVATE)
        val savedUriString = sharedPref.getString(KEY_PROFILE_IMAGE_URI, null)

        if (savedUriString != null) {
            try {
                val uri = Uri.parse(savedUriString)
                profileImageView.setImageURI(uri)
            } catch (e: Exception) {
                Log.e("CampusActivity", "Error loading profile image URI: ${e.message}")
                profileImageView.setImageResource(R.drawable.default_profile)
                // 오류 발생 시 저장된 URI 제거하여 다시 로드 시도하지 않도록
                sharedPref.edit().remove(KEY_PROFILE_IMAGE_URI).apply()
            }
        } else {
            profileImageView.setImageResource(R.drawable.default_profile)
        }
    }
}