
plugins {
    id("java")
}
// Keep local builds reproducible when the source is distributed without .git metadata.
version = providers.gradleProperty("pluginVersion").orElse("4.4.9-20261015").get()
extra["gitVersionString"] = version.toString()
allprojects {
    extra["gitVersionString"] = version.toString()
}
println("ThePitUltimate version: $version")
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
