import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.Scanner
import java.util.HexFormat
import java.security.MessageDigest
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
    id("io.papermc.paperweight.userdev")
    kotlin("plugin.lombok") version "2.1.20"
    id("io.freefair.lombok") version "8.10"
    kotlin("jvm") version "2.1.20"
    alias(libs.plugins.shadow)
}

group = "top.mcrw"

version = rootProject.version
// Paper resolves these libraries before constructing the plugin class loader.
// Keep them on Gradle's classpaths for compilation and tests, but out of Shadow.
val paperLibraries by configurations.creating {
    isCanBeConsumed = false
}
configurations.implementation { extendsFrom(paperLibraries) }
val paperLibraryArtifacts = providers.provider {
    paperLibraries.resolvedConfiguration.resolvedArtifacts.sortedBy { it.moduleVersion.id.toString() }
}
val paperLibraryCoordinates = paperLibraryArtifacts.map { artifacts ->
    artifacts.map { artifact ->
        require(artifact.classifier == null && artifact.extension == "jar") {
            "Unsupported Paper library: $artifact"
        }
        artifact.moduleVersion.id.toString()
    }
}
val paperLibraryModules = paperLibraryArtifacts.map { artifacts ->
    artifacts.map { "${it.moduleVersion.id.group}:${it.moduleVersion.id.name}" }.toSet()
}
val paperLibraryResources = layout.buildDirectory.dir("generated/paper-libraries")
val generatePaperLibraries by tasks.registering {
    inputs.files(paperLibraries)
    inputs.property("coordinates", paperLibraryCoordinates)
    outputs.dir(paperLibraryResources)
    doLast {
        val manifest = paperLibraryResources.get().file("paper-libraries.txt").asFile
        manifest.parentFile.mkdirs()
        manifest.writeText(paperLibraryArtifacts.get().joinToString("\n", postfix = "\n") { artifact ->
            val digest = MessageDigest.getInstance("SHA-256").digest(artifact.file.readBytes())
            "${artifact.moduleVersion.id} ${HexFormat.of().formatHex(digest)}"
        }, Charsets.UTF_8)
    }
}
sourceSets.main { resources.srcDir(paperLibraryResources) }
tasks.processResources {
    dependsOn(generatePaperLibraries)
    inputs.property("paperLibraries", paperLibraryCoordinates)
    filesMatching("plugin.yml") {
        filter { line: String ->
            if (line == "# @paper-libraries@") {
                paperLibraryCoordinates.get().joinToString("\n", prefix = "libraries:\n") { "  - $it" }
            } else line
        }
    }
}
repositories {
    maven("https://mvn.lumine.io/repository/maven-public/") {
        content { includeGroup("io.lumine") }
    }
    maven("https://repo.papermc.io/repository/maven-public/")
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
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
    archiveFileName.set("ThePitGay-High.jar")
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "META-INF/INDEX.LIST")
    filesMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }

    exclude("org.hamcrest")

    exclude("org.intellij")

    exclude("org.jetbrains")
    relocate("pku.yim.license", "net.mizukilab.pit.license")
    relocate("panda", "net.mizukilab.pit.libs")
    relocate("dev.rollczi", "net.mizukilab.pit.libs")
    relocate("cn.hutool", "net.mizukilab.pit.libs")
    // Paper and Fancy share Adventure types in public JVM method signatures.
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
    exclude("junit/**", "org/junit/**")
    from("build/tmp/processed-resources")
    mergeServiceFiles()
    dependencies {
        exclude { dependency ->
            "${dependency.moduleGroup}:${dependency.moduleName}" in paperLibraryModules.get()
        }
    }
}
dependencies {
    implementation(project(":paper-platform"))
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    compileOnly("maven.modrinth:worldedit:2YDdVDmG")
    compileOnly("maven.modrinth:fancynpcs:2.9.2")
    compileOnly("maven.modrinth:fancyholograms:2.10.1")
    compileOnly("io.lumine:Mythic-Dist:5.12.1") { isTransitive = false }
    implementation(project(":mythic-framework-bukkit"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.platform:junit-platform-launcher:1.11.4")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")
    testImplementation("org.mockito:mockito-core:5.18.0")

    compileOnly(fileTree("../packLib"))
    compileOnly(fileTree(mapOf("dir" to "../libs", "include" to listOf("*.jar"),
        "exclude" to listOf("citizens*.jar", "Citizens*.jar", "*spigot*.jar", "WorldEdit.jar", "FastAsyncWorldEdit*.jar", "mythicmobs*.jar"))))
    implementation(libs.reflectionhelper)

    implementation(libs.hutool.crypto)
    implementation(libs.book)
    compileOnly(libs.slf4j)
    implementation(libs.litecommands)
    compileOnly("com.caoccao.javet:javet:3.1.4")
    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")
    compileOnly(libs.luckperms)
    compileOnly("com.github.f4b6a3:uuid-creator:6.0.0")
    compileOnly(libs.papi)
    add(paperLibraries.name, libs.narshorn)
    compileOnly(files(rootProject.file("vendor/dependencies/ProtocolLib-5.5.0-SNAPSHOT-Spigot.jar")))
    add(paperLibraries.name, libs.jedis)
    add(paperLibraries.name, "org.mongojack:mongojack:5.0.1")
    add(paperLibraries.name, "org.mongodb:mongodb-driver-sync:5.2.0")

    compileOnly(libs.fastutil)
    compileOnly("us.crazycrew.crazycrates:api:0.7")
    compileOnly(libs.luckperms)
    compileOnly(libs.playerpoints)
    add(paperLibraries.name, kotlin("reflect"))
}
sourceSets.test {
    compileClasspath += configurations.compileClasspath.get()
    runtimeClasspath += configurations.compileClasspath.get()
}
tasks.test {
    useJUnitPlatform()
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
    jvmToolchain(21)
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

tasks.register<JavaExec>("paperLibrarySmoke") {
    group = "verification"
    description = "Exercise Paper's native library loader with a fresh cache and then offline."
    dependsOn(tasks.testClasses, tasks.shadowJar)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("net.mizukilab.pit.bootstrap.PaperLibrariesSmokeTool")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    environment("PAPER_DEFAULT_CENTRAL_REPOSITORY", "https://repo.maven.apache.org/maven2/")
    args(tasks.named<ShadowJar>("shadowJar").get().archiveFile.get().asFile.absolutePath,
        layout.buildDirectory.dir("paper-library-smoke").get().asFile.absolutePath)
}
