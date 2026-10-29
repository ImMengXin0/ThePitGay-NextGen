plugins { `java-library` }
repositories { mavenCentral() }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(17)); withSourcesJar() }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release.set(17) }
dependencies {
    api(project(":mythic-framework-core"))
    compileOnly("it.unimi.dsi:fastutil:8.5.15")
    compileOnly(files(rootProject.file(providers.gradleProperty("pandaSpigotJar")
        .orElse("libs/pandaspigot-221.jar").get())))
    compileOnly(files(rootProject.file("core/libs/spigot-1.8.8-R0.1-SNAPSHOT.jar")))
    compileOnly(libs.protocollib)
    compileOnly(libs.luckperms)
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-inline:4.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
sourceSets.test {
    compileClasspath += configurations.compileClasspath.get()
    runtimeClasspath += configurations.compileClasspath.get()
}
tasks.test { useJUnitPlatform() }
