plugins {
    alias(libs.plugins.android.library)
}

// A Kotlin port of the Rigby2D runtime: plays Spine 3.8 skeleton JSON rigs on an Android Canvas.
// See README.md.
android {
    namespace = "dev.gavenda.rigby"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 36
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // ParityTest compares against Rigby2D's JavaScript runtime with rigs from this folder.
    val parity = providers.gradleProperty("rigby.parity").orElse("")
    testOptions.unitTests.all { it.systemProperty("rigby.parity", parity.get()) }
}

dependencies {
    // Only the JSON tree (no generated serializers), to read skeleton files.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
