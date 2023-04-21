plugins {
    id("server-lib")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(libs.ksp)
    implementation(projects.libs.sqlGeneratorApi)
}
