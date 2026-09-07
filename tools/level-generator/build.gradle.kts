plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

application {
    mainClass.set("com.sodogku.tools.levelgen.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootDir
}

dependencies {
    implementation(projects.libraries.puzzle)
    implementation(projects.libraries.levels)
}
