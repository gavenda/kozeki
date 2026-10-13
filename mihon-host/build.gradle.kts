plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

// The API Mihon extensions are written against, under the package names they were compiled with.
// Extensions look these classes up by name through the host's class loader, so none can be renamed.
android {
    namespace = "eu.kanade.tachiyomi.source"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 36
        consumerProguardFiles("consumer-proguard.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Extensions see these through the host's class loader, so the app has to ship them too.
    api(libs.okhttp)
    // Extensions build their own clients with these, so they have to be in the app for them to find.
    api(libs.okhttp.brotli)
    api(libs.okhttp.dnsoverhttps)
    api(libs.okhttp.zstd)
    api(libs.okhttp.logging)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.serialization.json.okio)
    api(libs.kotlinx.serialization.protobuf)
    api(libs.xmlutil.core)
    api(libs.xmlutil.serialization)
    api(libs.injekt)
    api(libs.rxjava)
    api(libs.jsoup)
    api(libs.quickjs)
    api(libs.androidx.preference)
    api(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.webkit)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
}
