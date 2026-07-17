pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        exclusiveContent {
            forRepository {
                maven { url = uri("https://maven.aliyun.com/repository/central") }
            }
            filter { includeGroup("com.arthenica") }
        }
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://dl.bintray.com/rikkaw/Shizuku") }
        maven { url = uri("https://api.xposed.info/") }
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
    }
}

rootProject.name = "Mira"
include(":app")
include(":dragonbones")
include(":terminal")
include(":mnn")
include(":llama")
include(":mmd")
include(":fbx")
include(":showerclient")
include(":quickjs")
