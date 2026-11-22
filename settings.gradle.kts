pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "SirenProject"
include(":siren", ":sample:composeApp", ":sample:androidApp", ":sample:desktopApp")
