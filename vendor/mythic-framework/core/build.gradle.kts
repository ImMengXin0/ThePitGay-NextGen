plugins { `java-library` }
repositories { mavenCentral() }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(17)); withSourcesJar() }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release.set(17) }
dependencies {
    api("org.reflections:reflections:0.10.2")
    compileOnly("it.unimi.dsi:fastutil:8.5.15")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.belerweb:pinyin4j:2.5.1")
    compileOnly("org.apache.logging.log4j:log4j-core:2.24.3")
    implementation("org.yaml:snakeyaml:2.2")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-inline:4.11.0")
    testRuntimeOnly("it.unimi.dsi:fastutil:8.5.15")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform() }
