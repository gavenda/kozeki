import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun localProperty(name: String, default: String = ""): String =
    localProperties.getProperty(name)?.trim().orEmpty().ifEmpty { default }

// Public metadata only, so an API key is enough. Set googleBooks.apiKey in local.properties.
val googleBooksApiKey = localProperty("googleBooks.apiKey")

// Hardcover OAuth public client: the client ID is not a secret, PKCE protects the flow.
val hardcoverClientId = localProperty("hardcover.clientId", "d5a3c778-a5b8-4fb1-819c-deb5e30ac364")

// Must match a redirect URI registered on the Hardcover developer app exactly.
val hardcoverRedirectUri = URI(localProperty("hardcover.redirectUri", "kozeki://oauth/hardcover"))

// read:catalog covers search. Reviews by other readers need read:social, and the user_books query
// they are read through is only open to tokens with a read:library scope. read:users adds who
// wrote each review. read:me:content (username and picture) and read:me:email only add the
// account shown in Settings.
// Scopes the developer app does not allow are dropped by Hardcover at consent time.
val hardcoverScopes = localProperty(
    "hardcover.scopes",
    "read:catalog read:social read:library:public read:users read:me:content read:me:email",
)

// AniList OAuth client. The implicit grant has no secret, so the ID is all the app holds.
val aniListClientId = localProperty("anilist.clientId", "53791")

// Must be the redirect URL registered on the AniList developer client exactly.
val aniListRedirectUri = URI(localProperty("anilist.redirectUri", "kozeki://oauth/anilist"))

android {
    namespace = "dev.gavenda.kozeki"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.gavenda.kozeki"
        minSdk = 36
        targetSdk = 37
        versionCode = 22
        versionName = "2.6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // A phone and the x86_64 emulator. The 32-bit and x86 copies of the native libraries were
        // about 20 MB of the APK.
        ndk {
            abiFilters += setOf("arm64-v8a", "x86_64")
        }

        buildConfigField("String", "GOOGLE_BOOKS_API_KEY", "\"$googleBooksApiKey\"")
        buildConfigField("String", "HARDCOVER_CLIENT_ID", "\"$hardcoverClientId\"")
        buildConfigField("String", "HARDCOVER_REDIRECT_URI", "\"$hardcoverRedirectUri\"")
        buildConfigField("String", "HARDCOVER_SCOPES", "\"$hardcoverScopes\"")

        buildConfigField("String", "ANILIST_CLIENT_ID", "\"$aniListClientId\"")
        buildConfigField("String", "ANILIST_REDIRECT_URI", "\"$aniListRedirectUri\"")

        manifestPlaceholders["aniListRedirectScheme"] = aniListRedirectUri.scheme
        manifestPlaceholders["aniListRedirectHost"] = aniListRedirectUri.host.orEmpty()
        manifestPlaceholders["aniListRedirectPath"] = aniListRedirectUri.path.orEmpty()

        manifestPlaceholders["hardcoverRedirectScheme"] = hardcoverRedirectUri.scheme
        manifestPlaceholders["hardcoverRedirectHost"] = hardcoverRedirectUri.host.orEmpty()
        manifestPlaceholders["hardcoverRedirectPath"] = hardcoverRedirectUri.path.orEmpty()
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Set release.storeFile, release.storePassword, release.keyAlias and release.keyPassword in
    // local.properties. Without them the release build is left unsigned.
    val releaseStoreFile = localProperty("release.storeFile")
    if (releaseStoreFile.isNotEmpty()) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = localProperty("release.storePassword")
                keyAlias = localProperty("release.keyAlias")
                keyPassword = localProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Required by Readium.
        isCoreLibraryDesugaringEnabled = true
    }
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "org.readium.r2.shared.ExperimentalReadiumApi",
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.browser)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material3.adaptive)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.materialKolor.core)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // Pinch and double-tap zoom for a comic page, which can be far larger than the screen.
    implementation(libs.telephoto.zoomable.image.coil)
    implementation(libs.okhttp)
    // Pages some sources serve as AVIF or JPEG XL, which the platform cannot decode at a webtoon's height.
    implementation(libs.avif.coder)
    implementation(libs.jxl.coder)

    // Asks Google for access to the user's Drive, which is where backups go.
    //noinspection LoginCredentials
    implementation(libs.play.services.auth)

    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)
    implementation(libs.readium.navigator.web.reflowable)
    implementation(libs.readium.navigator.web.fixedlayout)

    implementation(libs.koin.core)
    // Mihon extensions look their dependencies up through Injekt; see exposeToExtensions().
    implementation(libs.injekt)
    implementation(project(":mihon-host"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
