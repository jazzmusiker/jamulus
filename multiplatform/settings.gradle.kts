// Copyright (c) 2026 Thomas Mueller
// Author(s): Thomas Mueller
// Licensed under AGPL 3.0 or any later version. See COPYING for details.

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "jamulus-compose"
include(":composeApp")
