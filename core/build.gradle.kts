import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.Scanner
import org.apache.tools.ant.filters.ReplaceTokens
import proguard.gradle.ProGuardTask
import java.io.ByteArrayOutputStream
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.7.0")
        classpath("org.ow2.asm:asm:9.7.1")
    }
}
plugins {
    kotlin("plugin.lombok") version "2.1.20"
    id("io.freefair.lombok") version "8.10"
    kotlin("jvm") version "2.1.20"
    alias(libs.plugins.shadow)
}

group = "top.mcrw"

version = "V"
repositories {
    exclusiveContent {
        forRepository { maven("https://repo.panda-lang.org/releases") }
        filter { includeGroup("dev.rollczi") }
    }
    exclusiveContent {
        forRepository { mavenCentral() }
        filter {
            includeGroup("junit")
            includeGroup("org.hamcrest")
            includeGroup("org.mockito")
            includeGroup("net.bytebuddy")
            includeGroup("org.objenesis")
        }
    }
    mavenCentral()
    maven("https://maven.aliyun.com/repository/public/") {
        content {
            includeGroupByRegex(".*")
        }
    }
    maven("https://maven.cleanroommc.com")
    maven("https://repo.crazycrew.us/releases")
    maven("https://repo.codemc.io/repository/nms/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://repo.dmulloy2.net/repository/public/")
    maven("https://repo.inventivetalent.org/content/groups/public/")
    maven("https://jitpack.io")
    maven("https://repo.rosewooddev.io/repository/public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}
val gitVersion = rootProject.extra["gitVersionString"].toString()

tasks.register<ProGuardTask>("proguard") {
    configuration(file("proguard.pro"))

    injars(tasks.named<Jar>("jar").flatMap { it.archiveFile })
    if (System.getProperty("java.version").startsWith("1.")) {
        libraryjars(file("${System.getProperty("java.home")}/lib/rt.jar"))
    } else {
        libraryjars(file("${System.getProperty("java.home")}/jmods/java.base.jmod"))
    }
    repositories
    libraryjars(configurations.compileClasspath)

    verbose()

    outjars(layout.buildDirectory.file("libs/proguard-tpu-minified.jar"))
}

tasks.named<ShadowJar>("shadowJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("ThePitGay-$version" + ".jar")
    exclude("META-INF/**")

    exclude("org.hamcrest")

    exclude("org.intellij")

    exclude("org.jetbrains")
    relocate("pku.yim.license", "net.mizukilab.pit.license")
    relocate("panda", "net.mizukilab.pit.libs")
    relocate("dev.rollczi", "net.mizukilab.pit.libs")
    relocate("cn.hutool", "net.mizukilab.pit.libs")
    relocate("net.kyori", "net.mizukilab.pit.libs")
    relocate("net.jodah", "net.mizukilab.pit.libs")
    // Preserve the NPC API descriptors consumed by existing extensions.
    relocate("cn.mythicland.npclib", "net.mizukilab.pit.libs.npclib")
    relocate("cn.mythicland.framework", "net.mizukilab.pit.libs.mythicframework")
    relocate("org.reflections", "net.mizukilab.pit.libs.mythicframework.reflections")
    relocate("javassist", "net.mizukilab.pit.libs.mythicframework.javassist")
    relocate("com.google.gson", "net.mizukilab.pit.libs.mythicframework.gson")
    relocate("net.sourceforge.pinyin4j", "net.mizukilab.pit.libs.mythicframework.pinyin4j")
    relocate("org.yaml.snakeyaml", "net.mizukilab.pit.libs.mythicframework.yaml")
    relocate("xyz.upperlevel.spigot", "net.mizukilab.pit.libs")
    exclude("kotlin/**", "junit/**", "org/junit/**")
    from("build/tmp/processed-resources")
    mergeServiceFiles()
}
dependencies {
    implementation(project(":mythic-framework-bukkit"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-inline:4.11.0")

    compileOnly(fileTree("../packLib"))
    // PandaSpigot adds the tracker/spawn events used by the source tree.
    compileOnly(files(rootProject.file(providers.gradleProperty("pandaSpigotJar")
        .orElse("libs/pandaspigot-221.jar").get())))
    compileOnly(fileTree(mapOf("dir" to "../libs", "include" to listOf("*.jar"),
        "exclude" to listOf("citizens*.jar", "Citizens*.jar"))))
    implementation(libs.reflectionhelper)

    implementation(libs.hutool.crypto)
    implementation(libs.book)
    compileOnly(libs.slf4j)
    implementation(libs.litecommands)
    implementation("zone.rong:imaginebreaker:2.1")
    compileOnly(libs.adventure.bukkit)
    compileOnly("com.caoccao.javet:javet:3.1.4")
    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")
    compileOnly(libs.luckperms)
    compileOnly("com.github.f4b6a3:uuid-creator:6.0.0")
    compileOnly(libs.papi)
    compileOnly(libs.narshorn)
    compileOnly(libs.protocollib)
    compileOnly(libs.jedis)
    compileOnly("org.mongojack:mongojack:5.0.1")
    compileOnly("org.mongodb:mongodb-driver-sync:5.2.0")

    compileOnly(fileTree("libs"))

    compileOnly(libs.fastutil)
    compileOnly("us.crazycrew.crazycrates:api:0.7")
    compileOnly(libs.luckperms)
    compileOnly(libs.playerpoints)
    compileOnly(libs.decentholograms)
    implementation(libs.adventure.bukkit)
    implementation(kotlin("reflect"))
}
sourceSets.test {
    compileClasspath += configurations.compileClasspath.get()
    runtimeClasspath += configurations.compileClasspath.get()
}
tasks.test {
    useJUnit()
    // Keep the 1.8 server's bundled libraries ahead of raw framework dependencies.
    // Production isolates those dependencies through Shadow relocation.
    classpath = files(rootProject.file(providers.gradleProperty("pandaSpigotJar")
        .orElse("libs/pandaspigot-221.jar").get()), file("libs/spigot-1.8.8-R0.1-SNAPSHOT.jar")) + classpath
    // Binary integration tests need the same relocated libraries shipped to Bukkit.
    dependsOn(tasks.named("shadowJar"))
    val repairExtendJar = rootProject.file(providers.gradleProperty("repairExtendJar")
        .orElse("test-fixtures/ThePitRepairExTend-4.0.0.jar").get())
    inputs.files(repairExtendJar).withPropertyName("repairExtendJar")
    systemProperty("repairExtendJar", repairExtendJar.absolutePath)
    for ((property, path) in mapOf(
        "pitAddonJar" to "test-fixtures/ThePitAddon-MistralNetWork-Server-6.7-SNAPSHOT.jar",
        "pitAddonLoaderJar" to "test-fixtures/ThePitAddon-MistralNetWork-Loader-6.7-SNAPSHOT.jar",
        "npcViewJar" to "test-fixtures/NPCView-1.0-SNAPSHOT.jar"
    )) {
        val fixture = rootProject.file(providers.gradleProperty(property).orElse(path).get())
        inputs.files(fixture).withPropertyName(property)
        systemProperty(property, fixture.absolutePath)
    }
    val nativeRepairJar = rootProject.file(providers.gradleProperty("nativeRepairJar")
        .orElse("test-fixtures/ThePitRepairExTend-5.3.0.jar").get())
    inputs.files(nativeRepairJar).withPropertyName("nativeRepairJar")
    systemProperty("nativeRepairJar", nativeRepairJar.absolutePath)
}
kotlin {
    jvmToolchain(17)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.named<JavaCompile>("compileJava") {
    // JVM descriptors include the return type. Preserve both the old List getter
    // and the current fastutil Map getter; Java source cannot express this overload.
    doLast {
        val classFile = destinationDirectory.file("cn/charlotte/pit/data/LeaderBoardEntry.class").get().asFile
        val reader = ClassReader(classFile.readBytes())
        val writer = ClassWriter(reader, 0)
        var hasBridge = false
        reader.accept(object : org.objectweb.asm.ClassVisitor(Opcodes.ASM9, writer) {
            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): org.objectweb.asm.MethodVisitor? {
                if (name == "getLeaderBoardEntries" && descriptor == "()Ljava/util/List;") hasBridge = true
                return super.visitMethod(access, name, descriptor, signature, exceptions)
            }
            override fun visitEnd() {
                if (!hasBridge) {
                    val method = super.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC or Opcodes.ACC_BRIDGE,
                        "getLeaderBoardEntries", "()Ljava/util/List;", null, null)
                    method.visitCode()
                    method.visitMethodInsn(Opcodes.INVOKESTATIC, "cn/charlotte/pit/data/LeaderBoardEntry",
                        "getLegacyLeaderBoardEntries", "()Ljava/util/List;", false)
                    method.visitInsn(Opcodes.ARETURN)
                    method.visitMaxs(1, 0)
                    method.visitEnd()
                }
                super.visitEnd()
            }
        }, 0)
        classFile.writeBytes(writer.toByteArray())
    }
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
tasks.check {
    dependsOn(":mythic-framework-core:test", ":mythic-framework-bukkit:test")
}
