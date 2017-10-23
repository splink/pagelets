name := """pagelets"""

import ReleaseTransformations._

lazy val root = (project in file(".")).
  settings(Seq(
    organization := "org.splink",
    scalaVersion := "2.12.2",
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "org.scalatestplus.play" %% "scalatestplus-play" % "3.0.0" % Test,
      "org.scalamock" %% "scalamock-scalatest-support" % "3.6.0" % Test,
      "org.mockito" % "mockito-core" % "1.10.19" % Test,
      "ch.qos.logback" % "logback-classic" % "1.1.7" % Test,
      "com.typesafe.play" %% "play" % "2.6.3",
      "com.typesafe.akka" %% "akka-stream" % "2.5.3"),
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-feature",
      "-language:implicitConversions",
      "-language:higherKinds",
      "-language:existentials")
  ) ++ publishSettings)

lazy val publishSettings = Seq(
  releaseCrossBuild := true,
  crossScalaVersions := Seq("2.11.11", "2.12.3"),
  publishMavenStyle := true,
  pomIncludeRepository := { _ => false },
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  licenses := Seq("Apache2 License" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
  homepage := Some(url("https://github.com/splink/pagelets")),
  scmInfo := Some(ScmInfo(url("https://github.com/splink/pagelets"), "scm:git:git@github.com:splink/pagelets.git")),
  pomExtra :=
    <developers>
      <developer>
        <id>splink</id>
        <name>Max Kugland</name>
        <url>http://splink.org</url>
      </developer>
    </developers>,
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    releaseStepCommandAndRemaining("+test"),
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    releaseStepCommandAndRemaining("+publishSigned"),
    setNextVersion,
    commitNextVersion,
    releaseStepCommandAndRemaining("sonatypeReleaseAll"),
    pushChanges
  )
)
