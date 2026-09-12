import com.android.build.api.dsl.ApplicationProductFlavor
import java.util.Properties

/**
 * Sets the app's name once per flavor, for both the launcher and the sessions list.
 *
 * <p>`app_name` is not in `res/values/strings.xml` any more: with two apps there is no sensible
 * default, and a flavor silently inheriting the other one's name is worse than a build failure.
 * `APP_LABEL` carries the same text into the device name sent at sign-in, so a phone with both apps
 * installed produces two distinguishable rows in Settings rather than the model name twice — which
 * matters when the point of that list is deciding which session to revoke.
 */
fun ApplicationProductFlavor.applyLabel(label: String) {
    resValue("string", "app_name", label)
    buildConfigField("String", "APP_LABEL", "\"$label\"")
}

/**
 * Gives each flavor its own deep-link scheme.
 *
 * <p>Both apps can be installed on one phone, and the notification deep link is registered
 * `BROWSABLE`, so a shared scheme would make any `prabhix://chat/{id}` link ambiguous: Android would
 * show an app chooser and could open a customer's conversation in the wrong app. Notifications
 * themselves are unaffected either way, since they target the activity explicitly, but the scheme
 * still has to be unique for anything arriving from outside.
 */
fun ApplicationProductFlavor.applyDeepLinkScheme(scheme: String) {
    manifestPlaceholders["deepLinkScheme"] = scheme
    buildConfigField("String", "DEEP_LINK_SCHEME", "\"$scheme\"")
}

/**
 * Points a flavor at its own OAuth client on Prabhix Identity.
 *
 * <p>Two apps, two client ids, because a redirect registered for one must not be usable by the other:
 * a shared client would let the customer app receive an authorization code minted for the staff app.
 * The redirect URI itself is not set here — it derives from the application id, which the debug build
 * type suffixes, so it is assembled per variant further down.
 */
fun ApplicationProductFlavor.applyOAuthClient(clientId: String) {
    buildConfigField("String", "OAUTH_CLIENT_ID", "\"$clientId\"")
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

/**
 * Applies the Google Services plugin only when Firebase is configured.
 *
 * <p>The plugin fails the build outright if `google-services.json` is missing, so applying it
 * unconditionally would mean nobody could build the app without Firebase credentials — including CI,
 * which has none. Applied conditionally, push works the moment the file is dropped in and the build
 * stays green until then, with [com.prabhix.operator.data.push.PushTokenManager] catching the
 * "Default FirebaseApp is not initialized" it gets in the meantime.
 *
 * <p>One file covers both flavors and both build types: a single Firebase project holds four Android
 * apps — `com.prabhix.operator`, `com.prabhix.admin` and the `.debug` variant of each — and the
 * downloaded JSON contains a client block for every one. See `app/google-services.json.template`.
 */
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// Release signing comes from keystore.properties, which is gitignored along with the .jks it
// points at. When the file is absent (CI, a fresh clone) the release build is left unsigned
// rather than failing, so debug builds and tests still work without the private key.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.prabhix.operator"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/api/v1\"")
        // Where sign-in happens. 10.0.2.2 is the host machine as seen from the emulator, so a
        // developer running the compose stack gets the real hosted login page rather than a stub.
        buildConfigField("String", "IDENTITY_ISSUER", "\"http://10.0.2.2:8081\"")
    }

    /**
     * Two apps from one module: the OneOps product sold to customers, and the private admin app.
     *
     * <p>Flavors rather than two modules or two repositories. Almost everything is identical between
     * them — the same API, the same token handling, the same eight tenant-scoped screens — so a
     * second copy would mean every fix landing twice and eventually only landing once. What differs
     * is the application id, the name and the icon, set below, plus the platform screens, which live
     * in `src/admin/` and are absent from the customer's build rather than merely switched off.
     *
     * <p>`oneops` is first, which makes it the default when a Gradle invocation names no flavor.
     */
    flavorDimensions += "app"

    productFlavors {
        create("oneops") {
            dimension = "app"
            // Deliberately still `operator`, which is what this app was called before the product
            // was named. The id is the app's identity to Play and to every phone that already has
            // it: changing it to match the label would publish a second, unrelated app and leave
            // existing installs on a version that never updates again. The label is what people
            // see, and that is free to change.
            applicationId = "com.prabhix.operator"
            applyLabel("Prabhix OneOps")
            applyDeepLinkScheme("prabhix")
            applyOAuthClient("prabhix-oneops-android")
            buildConfigField("String", "DEVICE_HEADER", "\"mobile-android\"")
        }
        create("admin") {
            dimension = "app"
            // A separate id, so both install side by side on one phone and Play treats them as the
            // two different products they are.
            applicationId = "com.prabhix.admin"
            applyLabel("Prabhix Admin")
            // No deep-link scheme, and no push. Every notification this platform sends addresses a
            // conversation or a mail thread, and this app has no screen to open one in — so it does
            // not advertise a scheme it would only have to ignore.
            applyOAuthClient("prabhix-admin-android")
            buildConfigField("String", "DEVICE_HEADER", "\"mobile-android-admin\"")
        }
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Null without keystore.properties, which yields an unsigned APK. That is intentional:
            // it keeps the build green for anyone without the key, and an unsigned APK simply
            // refuses to install rather than shipping something signed by a throwaway debug key.
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("String", "API_BASE_URL", "\"https://api.prabhixtechnologies.com/api/v1\"")
            // Same public issuer as web consoles. id.prabhixtechnologies.com is reserved until its DNS
                // and Caddy host exist; pointing release builds there would break sign-in today.
                buildConfigField("String", "IDENTITY_ISSUER", "\"https://api.prabhixtechnologies.com\"")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

/**
 * Gives every variant a redirect URI matching its own application id.
 *
 * <p>The scheme has to be the application id, including the debug suffix. AppAuth's
 * `RedirectUriReceiverActivity` filters on `appAuthRedirectScheme`, and Android resolves a private-use
 * scheme across all installed apps — so a debug build claiming the release build's scheme would make
 * the redirect ambiguous on a developer's phone, and the authorization code could land in the wrong
 * app. Both spellings are registered server-side in Identity's client list.
 *
 * <p>Set here rather than on the flavor because only the variant knows the suffix.
 */
androidComponents {
    onVariants { variant ->
        val scheme = variant.applicationId
        variant.manifestPlaceholders.put("appAuthRedirectScheme", scheme)
        variant.buildConfigFields.put(
            "OAUTH_REDIRECT_URI",
            scheme.map {
                com.android.build.api.variant.BuildConfigField(
                    "String",
                    "\"$it:/oauth2redirect\"",
                    "Redirect back into this exact app after sign-in.",
                )
            },
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.sse)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.appauth)
    implementation(libs.androidx.browser)
    // OneOps only. Push notifications here address a conversation or a mail thread, and the admin
    // app has no screen to open one — so it needs neither the SDK nor the service the SDK's own
    // manifest contributes, which was the last piece of the product left in the staff APK.
    // Quoted because flavor configurations are created by the Android plugin after this block is
    // type-checked, so the Kotlin DSL has no generated accessor for them.
    "oneopsImplementation"(platform(libs.firebase.bom))
    "oneopsImplementation"(libs.firebase.messaging)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
