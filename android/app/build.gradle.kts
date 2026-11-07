import java.util.Properties

plugins { id("com.android.application") }

val localConfig = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val mapsKey = providers.environmentVariable("MAPS_API_KEY")
    .orElse(providers.gradleProperty("MAPS_API_KEY"))
    .orElse(localConfig.getProperty("MAPS_API_KEY", ""))

android {
    namespace = "com.comeforfood.saferoute"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.comeforfood.saferoute"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-tv1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey.get()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { viewBinding = true; buildConfig = true }
    buildTypes {
        debug { buildConfigField("String", "API_BASE_URL", "\"" + providers.gradleProperty("API_BASE_URL").orElse("http://10.0.2.2:8000/").get() + "\"") }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "API_BASE_URL", "\"" + providers.gradleProperty("API_BASE_URL").orElse("https://localhost/").get() + "\"")
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Optional Windows 8.3 path for Robolectric runtime caches under a user name with spaces.
            providers.gradleProperty("robolectricRuntimeDir").orNull?.let { directory ->
                it.systemProperty("robolectric.dependency.dir", directory)
            }
            it.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED",
                "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"
            )
        }
    }
}
dependencies {
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.7")
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}
