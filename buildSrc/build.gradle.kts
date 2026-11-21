plugins { `kotlin-dsl` }
repositories { mavenCentral() }
dependencies {
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("com.google.code.gson:gson:2.13.2")
    testImplementation(kotlin("test-junit"))
}

