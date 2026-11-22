plugins { `kotlin-dsl` }
repositories { mavenCentral() }
dependencies {
    implementation("com.google.code.gson:gson:2.13.2")
    testImplementation(kotlin("test-junit"))
}
