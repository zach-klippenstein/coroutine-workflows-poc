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
  compile(project(":workflows-jvm"))
  compile(kotlin("stdlib-jdk8"))
  compile("io.reactivex.rxjava2:rxjava:2.2.2")
  compile("org.jetbrains.kotlinx:kotlinx-coroutines-rx2:0.26.0-eap13")
  testCompile("org.jetbrains.kotlin:kotlin-test-common:1.3-M2")
  testCompile("org.jetbrains.kotlin:kotlin-test-annotations-common:1.3-M2")
  testCompile("org.jetbrains.kotlin:kotlin-test-junit:1.3-M2")
}

tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = "1.8"
}
