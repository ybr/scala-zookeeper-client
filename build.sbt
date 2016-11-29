name := """scala-zookeeper-client"""

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.7"

crossScalaVersions := Seq("2.10.5")

// disable -Xfatal-warnings due to java.time.Duration stub warning
scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xlint", "-Ywarn-value-discard", "-Ywarn-unused-import", "-Ywarn-dead-code" /*, "-Xfatal-warnings" */)

javacOptions ++= Seq("-Xlint:unchecked")

libraryDependencies ++= Seq(
  "org.apache.zookeeper" % "zookeeper" % "3.4.9",
  "com.typesafe.akka" %% "akka-stream" % "2.4.11"
)