import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM module: Android APIs are not on the classpath, so the compiler
// enforces "core must not depend on Android" (design §5.2, §49).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    testImplementation(libs.junit)
}

// M1 exit criterion: core coverage >= 80% (plan §1.1).
kover {
    reports {
        verify {
            rule {
                minBound(80)
            }
        }
    }
}
