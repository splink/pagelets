name := """pagelets"""
version := "0.0.3"

lazy val root = (project in file(".")).
  settings(Seq(
    organization := "org.splink",
    scalaVersion := "2.11.8",
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test,
      "org.scalamock" %% "scalamock-scalatest-support" % "3.2.2" % Test,
      "org.mockito" % "mockito-core" % "1.10.19" % Test,
      "ch.qos.logback" % "logback-classic" % "1.1.7" % Test,
      "com.typesafe.play" %% "play" % "2.5.10",
      "com.typesafe.akka" %% "akka-stream" % "2.4.10"),
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-feature",
      "-language:implicitConversions",
      "-language:higherKinds",
      "-language:existentials")
  ) ++ publishSettings)


import ReleaseTransformations._

lazy val publishSettings = Seq(
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
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    ReleaseStep(action = Command.process("publishSigned", _)),
    setNextVersion,
    commitNextVersion,
    ReleaseStep(action = Command.process("sonatypeReleaseAll", _)),
    pushChanges
  )
)
