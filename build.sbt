name := """scala-zookeeper-client"""

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.8"

crossScalaVersions := Seq("2.10.5")

// disable -Xfatal-warnings due to java.time.Duration stub warning
scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xlint", "-Ywarn-value-discard", "-Ywarn-unused-import", "-Ywarn-dead-code" /*, "-Xfatal-warnings" */)

javacOptions ++= Seq("-Xlint:unchecked")

libraryDependencies ++= Seq(
  "org.apache.zookeeper" % "zookeeper" % "3.4.9",
  "com.typesafe.akka" %% "akka-stream" % "2.4.11",
  "commons-logging" % "commons-logging" % "1.2",
  "ch.qos.logback" % "logback-classic" % "1.1.3" % Test,
  "org.slf4j" % "jcl-over-slf4j" % "1.7.12" % Test,
  "org.slf4j" % "log4j-over-slf4j" % "1.7.12" % Test
)