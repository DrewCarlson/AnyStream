plugins {
    id("server-lib")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(libsCommon.ksp)
    implementation(projects.libs.sqlGeneratorApi)
}
