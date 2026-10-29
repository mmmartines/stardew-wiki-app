import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Assinatura de release: lida de keystore.properties (local) ou de variáveis de ambiente (GitHub Actions).
val signing = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String, env: String): String? = signing.getProperty(key) ?: System.getenv(env)

// Ícones do jogo no menu inicial: ligados por padrão; a versão da loja pode desligar com -PgameIcons=false
val gameIcons = (project.findProperty("gameIcons") as String?)?.toBoolean() ?: true

android {
    namespace = "com.stardew.wikipt"
    compileSdk = 36

    defaultConfig {
        // ID definitivo na loja: não pode mudar depois de publicado
        applicationId = "io.github.mmmartines.guiadovale"
        minSdk = 21 // Android 5.0+: inclui aparelhos antigos ainda muito usados
        targetSdk = 36
        versionCode = 6
        versionName = "1.4.1"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("boolean", "GAME_ICONS", gameIcons.toString())
    }

    signingConfigs {
        create("release") {
            val store = secret("storeFile", "SIGNING_STORE_FILE")
            if (store != null) {
                storeFile = file(store)
                storePassword = secret("storePassword", "SIGNING_STORE_PASSWORD")
                keyAlias = secret("keyAlias", "SIGNING_KEY_ALIAS")
                keyPassword = secret("keyPassword", "SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-teste"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (secret("storeFile", "SIGNING_STORE_FILE") != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures {
        buildConfig = true
    }
    androidResources {
        generateLocaleConfig = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
