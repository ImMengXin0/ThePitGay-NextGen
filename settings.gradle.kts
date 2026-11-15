plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "ThePitGay-High"

include("core")
include("paper-platform")
include("mythic-framework-core", "mythic-framework-bukkit")
project(":mythic-framework-core").projectDir = file("vendor/mythic-framework/core")
project(":mythic-framework-bukkit").projectDir = file("vendor/mythic-framework/bukkit")
