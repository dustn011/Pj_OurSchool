plugins {
    id("com.android.application") // 기존 플러그인 유지
    id("org.jetbrains.kotlin.android") // 기존 플러그인 유지
    id("com.google.gms.google-services") // Firebase GMS 플러그인 유지
    // secrets-gradle-plugin은 API 키 관리를 위해 추가 (두 번째 파일에서 확인됨)

}

android {
    namespace = "com.example.pj_ourschool" // 기존 namespace 유지
    compileSdk = 36 // 기존 compileSdk 유지

    defaultConfig {
        applicationId = "com.example.pj_ourschool" // 기존 ID 유지
        minSdk = 34 // 기존 minSdk 유지
        targetSdk = 36 // 기존 targetSdk 유지
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 기존 BuildConfigField 유지 (카카오 맵 키)
        buildConfigField("String", "KAKAO_MAP_KEY", "\"8657f921e8595e3efa4a2e0663545bbe\"")

        // 기존 NDK 설정 유지
        ndk {
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
            abiFilters.add("x86")
            abiFilters.add("x86_64")
        }
    }

    buildFeatures {
        viewBinding = true // 기존 ViewBinding 유지
        buildConfig = true // API 키 사용을 위해 유지 (두 번째 파일 및 BuildConfigField에 의해)
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // 컴파일 옵션을 더 높은 버전으로 통합 (두 번째 파일 기준 Java 11)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11" // 컴파일 옵션과 일치하도록 설정
    }
}

dependencies {

    // ===============================================
    // ✅ 기존 프로젝트 종속성 (카카오, Firebase, Retrofit)
    // ===============================================

    // 카카오 맵
    implementation("com.kakao.maps.open:android:2.12.13")

    // 카카오 SDK
    // v2-user와 v2-all 중 v2-all이 모든 기능을 포함하므로 하나만 남깁니다.
    implementation("com.kakao.sdk:v2-all:2.21.2")

    // 서클 이미지 뷰
    implementation("de.hdodenhof:circleimageview:3.1.0")

    // Firebase 인증
    implementation("com.google.firebase:firebase-auth:22.3.0")

    // 위치 서비스 (중복되므로 하나만 남김)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // JTDS (MSSQL 접속 라이브러리)
    implementation ("net.sourceforge.jtds:jtds:1.3.1")


    // ===============================================
    // ✅ 통합 및 XML/KTX 필수 종속성
    // ===============================================

    // 핵심 KTX (버전을 최신 안정화 버전으로 통일)
    // 두 파일의 버전 중 최신 버전 (1.15.0) 유지
    implementation("androidx.core:core-ktx:1.15.0")

    // AppCompatActivity (두 파일의 버전 중 최신 버전인 1.7.0 유지)
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Material Design (두 파일의 버전 중 최신 버전인 1.12.0 유지)
    implementation("com.google.android.material:material:1.12.0")

    // ConstraintLayout (두 파일의 버전 중 최신 버전인 2.2.0 유지)
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // activity-ktx (두 번째 파일에서 추가됨 - viewModels() 사용을 위해)
    implementation("androidx.activity:activity-ktx:1.9.0")

    // ViewModel 및 Coroutines (두 번째 파일에서 추가됨)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // ===============================================
    // ✅ Gemini API 종속성 (두 번째 파일에서 추가됨)
    // ===============================================
    // libs.generativeai 대신 직접 버전을 사용
    implementation("com.google.ai.client.generativeai:generativeai:0.8.0") // 예시 버전 사용, 최신 버전 확인 필요

    // ===============================================
    // ✅ 테스트 종속성
    // ===============================================
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}