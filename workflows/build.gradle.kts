import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
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
  compile(kotlin("stdlib-jdk8"))
  compile("org.jetbrains.kotlinx:kotlinx-coroutines-core:0.26.0-eap13")
  testCompile("junit", "junit", "4.12")
}

tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = "1.8"
}
