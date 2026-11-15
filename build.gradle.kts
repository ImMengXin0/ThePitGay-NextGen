
plugins {
    id("java")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.18" apply false
}
// Keep local builds reproducible when the source is distributed without .git metadata.
version = providers.gradleProperty("pluginVersion").orElse("5.0.0-HIGH-SNAPSHOT").get()
extra["gitVersionString"] = version.toString()
allprojects {
    extra["gitVersionString"] = version.toString()
}
println("ThePitGay-High version: $version")
tasks.named<Jar>("jar") {
    enabled = false
}
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}
repositories {
    mavenCentral()
}
