plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

// When google-services.json is present, add to this file:
// plugins { id("com.google.gms.google-services") version "4.4.2" apply false }
// And to app/build.gradle.kts: id("com.google.gms.google-services")
