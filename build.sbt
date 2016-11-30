name := """scala-zookeeper-client"""

organization := "com.github.ybr"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.8"

// disable -Xfatal-warnings due to java.time.Duration stub warning
scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-Ywarn-dead-code",
  /*, "-Xfatal-warnings" */
  "-Ywarn-unused-import",
  "-Ywarn-value-discard"
)

javacOptions ++= Seq(
  "-Xlint:unchecked"
)

libraryDependencies ++= Seq(
  "org.apache.zookeeper" % "zookeeper" % "3.4.9",
  "com.typesafe.akka" %% "akka-stream" % "2.4.11",
  "commons-logging" % "commons-logging" % "1.2",
  "ch.qos.logback" % "logback-classic" % "1.1.3" % Test,
  "org.slf4j" % "jcl-over-slf4j" % "1.7.12" % Test,
  "org.slf4j" % "log4j-over-slf4j" % "1.7.12" % Test
)

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

publishTo <<= version { (v: String) =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

credentials += Credentials("Sonatype Nexus Repository Manager",
                            "oss.sonatype.org",
                            System.getenv.get("SONATYPE_USER"),
                            System.getenv.get("SONATYPE_PASS"))

pomExtra := (
  <url>https://github.com/ybr/scala-zookeeper-client.git</url>
  <licenses>
    <license>
      <name>MIT</name>
      <url>https://opensource.org/licenses/MIT</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:ybr/scala-zookeeper-client.git</url>
    <connection>scm:git:git@github.com:ybr/scala-zookeeper-client.git</connection>
  </scm>
  <developers>
    <developer>
      <id>ybr</id>
      <name>Yohann Bredoux</name>
      <url>http://ybr.github.io</url>
    </developer>
  </developers>)