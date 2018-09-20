import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("kotlin-platform-common")
}

group = "com.zachklipp.workflow"
version = "1.0-SNAPSHOT"

repositories {
  maven { setUrl("http://dl.bintray.com/kotlin/kotlin-eap") }
  maven { setUrl("http://dl.bintray.com/kotlin/kotlin-dev") }
  mavenCentral()
}

dependencies {
  compile(kotlin("stdlib-common"))
  compile("org.jetbrains.kotlinx:kotlinx-coroutines-core-common:0.26.0-eap13")
}
