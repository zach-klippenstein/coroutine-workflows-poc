import org.gradle.internal.deployment.RunApplication
import org.gradle.internal.impldep.aQute.bnd.build.Run
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  java
  application
  kotlin("jvm")
}

group = "com.zachklipp.workflow"
version = "1.0-SNAPSHOT"

repositories {
  maven { setUrl("http://dl.bintray.com/kotlin/kotlin-eap") }
  maven { setUrl("http://dl.bintray.com/kotlin/kotlin-dev") }
  mavenCentral()
}

dependencies {
  compile(project(":workflows-rx2"))
  compile(kotlin("stdlib-jdk8"))
  compile(kotlin("reflect"))
  compile("org.jetbrains.kotlinx:kotlinx-coroutines-core:0.26.0-eap13")
  compile("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:0.26.0-eap13")
  testCompile("junit", "junit", "4.12")
}

configure<ApplicationPluginConvention> {
  mainClassName = "com.zachklipp.workflows.app.HelloAppKt"
//  mainClassName = "com.zachklipp.workflows.app.HelloCliAppKt"
}

configure<JavaPluginConvention> {
  sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<JavaExec> {
  standardInput = System.`in`
}
