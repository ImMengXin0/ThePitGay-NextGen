plugins {
    `java-library`
    id("io.papermc.paperweight.userdev")
}
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)); withSourcesJar() }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release.set(21) }
dependencies {
    api(project(":mythic-framework-core"))
    compileOnly("it.unimi.dsi:fastutil:8.5.15")
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    compileOnly(files(rootProject.file("vendor/dependencies/ProtocolLib-5.5.0-SNAPSHOT-Spigot.jar")))
    compileOnly(libs.luckperms)
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.18.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
sourceSets.test {
    compileClasspath += configurations.compileClasspath.get()
    runtimeClasspath += configurations.compileClasspath.get()
}
tasks.test { useJUnitPlatform() }
