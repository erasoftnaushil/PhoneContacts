import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    alias(libs.plugins.google.gms.google.services)
}

android {
    compileSdk = libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        applicationId = "com.contactsapp2025.universal"
        minSdk = 26
        targetSdk = libs.versions.app.build.targetSDK.get().toInt()
        versionCode = 26
        versionName = "2.6"
        setProperty("archivesBaseName", "Contacts_$versionName.$versionCode")
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

        buildConfigField("String", "API_SECRET", "\"${properties["API_SECRET"]}\"")

        manifestPlaceholders["specialUseFgsSubtype"] = "User-enabled post-call alarm feature that detects call end events and launches an alarm-setting screen with a visible foreground notification. The service runs only for the duration of the post-call action and stops immediately after completion."
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions.add("variants")
    productFlavors {
        register("core")
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
    }

    compileOptions {
        val currentJavaVersionFromLibs = JavaVersion.valueOf(libs.versions.app.build.javaVersion.get())
        sourceCompatibility = currentJavaVersionFromLibs
        targetCompatibility = currentJavaVersionFromLibs
    }

    dependenciesInfo {
        includeInApk = false
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    tasks.withType<KotlinCompile> {
        compilerOptions.jvmTarget.set(
            JvmTarget.fromTarget(project.libs.versions.app.build.kotlinJVMTarget.get())
        )
    }

    namespace = "com.contactsapp2025.universal"

    lint {
        checkReleaseBuilds = false
        abortOnError = true
        warningsAsErrors = false
        baseline = file("lint-baseline.xml")
        lintConfig = rootProject.file("lint.xml")
    }

    bundle {
        language {
            enableSplit = true
        }
    }
}

configurations.configureEach {
    exclude(group = "com.google.android.gms", module = "play-services-ads")
    exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
}

tasks.matching { it.name.startsWith("check") && it.name.endsWith("AarMetadata") }.configureEach {
    enabled = false
}


detekt {
    baseline = file("detekt-baseline.xml")
    config.setFrom("$rootDir/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false
}

dependencies {
    implementation(libs.material)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.autofittextview)
    implementation(libs.ezvcard)
    implementation(libs.indicatorfastscroll)
    implementation(libs.bundles.room)
    implementation(libs.sdp.android)
    implementation(libs.firebase.config)
    implementation("com.google.firebase:firebase-analytics-ktx:21.5.0")
    implementation("com.google.firebase:firebase-messaging-ktx:23.4.1")

    ksp(libs.androidx.room.compiler)
    detektPlugins(libs.compose.detekt)
    implementation(libs.lottie)
    implementation(libs.androidx.lifecycle.process)
    implementation(project(":commons"))
//    implementation(project(":permissionsdk"))
    implementation("com.karumi:dexter:6.2.3")
    implementation("com.guolindev.permissionx:permissionx:1.8.1")

    implementation(libs.androidx.lifecycle.viewModel)
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.core:core-splashscreen:1.2.0")

    // Permission API
    implementation(libs.retrofit)
    implementation(libs.gson)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)

    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation(libs.okhttp)
    implementation("com.google.android.gms:play-services-ads-identifier:18.0.1")
    implementation("com.google.android.gms:play-services-time:16.0.1")

    implementation("com.google.android.gms:play-services-appset:16.1.0")

    implementation("com.amplitude:analytics-android:1.+")

    implementation(libs.call.sdk)
    implementation(libs.ad.sdk)
    implementation(libs.notification.sdk)

    implementation("com.android.billingclient:billing:9.1.0")
    implementation("com.facebook.shimmer:shimmer:0.5.0")

}
