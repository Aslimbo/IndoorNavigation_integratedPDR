pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") } // 添加 JitPack 仓库
    }
}
dependencyResolutionManagement {
//    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS) // 更改为 PREFER_SETTINGS 以允许在 settings 中配置仓库
    repositories {
        google()
        mavenCentral()
//        maven { url = uri("https://jitpack.io") } // 添加 JitPack 仓库
        maven { url = uri("https://jitpack.io") } // 添加 JitPack 仓库
    }
}

rootProject.name = "Locate"
include(":app")
