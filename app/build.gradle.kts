plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "br.com.lojabaterias"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.lojabaterias"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = "1.0.${System.getenv("VERSION_CODE") ?: "0"}"

        // Nuvem (Supabase). A chave "anon" é pública por natureza: o acesso aos dados é protegido
        // pelas regras de segurança (RLS) e pela conta interna da loja. Pode ser trocada por secrets do GitHub.
        val supabaseUrl = System.getenv("SUPABASE_URL")?.takeIf { it.isNotBlank() }
            ?: "https://ogodqrhtbbwdnsnuqqfq.supabase.co"
        val supabaseKey = System.getenv("SUPABASE_ANON_KEY")?.takeIf { it.isNotBlank() }
            ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im9nb2Rxcmh0YmJ3ZG5zbnVxcWZxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAyNTgzODIsImV4cCI6MjEwNTgzNDM4Mn0.KZ8bPSzNwTul6qnSF0U_kPpSZxUiqYZO4PFgZ-UNd2k"
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseKey\"")
    }

    // Assinatura fixa: mantém a mesma chave entre builds para que o app possa ser
    // atualizado no celular sem desinstalar (e sem perder os dados).
    // Pode ser substituída por secrets do GitHub (ver README).
    signingConfigs {
        create("release") {
            val customKeystore = System.getenv("SIGNING_KEYSTORE_PATH")
            if (!customKeystore.isNullOrBlank()) {
                storeFile = file(customKeystore)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            } else {
                storeFile = file("signing/loja-baterias.jks")
                storePassword = "lojabaterias"
                keyAlias = "lojabaterias"
                keyPassword = "lojabaterias"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "2g"
                it.testLogging {
                    events("failed")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                    showStackTraces = false
                }
            }
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = true
        disable += setOf("MissingTranslation", "GradleDependency", "OldTargetApi", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }
}

// Esquemas do banco versionados no repositório (necessários para migrações seguras).
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    // Testes de banco (Room/SQLite) na JVM, sem emulador
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
}
