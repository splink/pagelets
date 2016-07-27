name := """raven"""

version := "0.1-SNAPSHOT"

lazy val commonSettings = Seq(
  scalaVersion := "2.11.8"
)

lazy val root = (project in file(".")).
  enablePlugins(PlayScala).
  dependsOn(macros).
  settings(commonSettings: _*).
  settings(
    // include the macro classes and resources in the main jar
    mappings in (Compile, packageBin) ++= mappings.in(macros, Compile, packageBin).value,
    // include the macro sources in the main source jar
    mappings in (Compile, packageSrc) ++= mappings.in(macros, Compile, packageSrc).value
  )

lazy val macros = (project in file("macros")).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "com.typesafe.play" %% "play" % "2.5.4",
      "com.typesafe.akka" %% "akka-stream" % "2.4.4")
      ,
    publish := {},
    publishLocal := {}
  )

libraryDependencies ++= Seq(
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test
)

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

