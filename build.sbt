Global / onChangedBuildSource := ReloadOnSourceChanges

enablePlugins(
  LinuxPlugin,
  JDebPackaging,
  DebianPlugin,
  DebianDeployPlugin,
  JavaServerAppPackaging,
  SystemloaderPlugin,
  SystemdPlugin)


lazy val root = (project in file("."))
  .aggregate(shared, server, web)
  .settings(
    crossScalaVersions := Nil,
    publish / skip := true
  )

lazy val akkaVersion = "2.6.4"
lazy val circeVersion = "0.13.0"
lazy val monocleVersion = "2.0.4" // depends on cats 2.x

lazy val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "com.github.julien-truffaut" %% "monocle-core" % monocleVersion withSources(),
    "com.github.julien-truffaut" %% "monocle-macro" % monocleVersion withSources(),
    "com.github.julien-truffaut" %% "monocle-law" % monocleVersion % "test" withSources(),
    "org.scalatest" %% "scalatest" % "3.1.1" % "test" withSources(),
  ),
  libraryDependencies ++= Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser",
    "io.circe" %% "circe-literal"
  ).map(_ % circeVersion),
  organization := "leibman.net",
  version := "0.1",
  scalaVersion := "2.13.1",
  startYear := Some(2020),
  organizationName := "Roberto Leibman",
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))
)

lazy val shared: Project = project.in(file("shared"))
  .settings(commonSettings)
  .enablePlugins(
    AutomateHeaderPlugin,
    GitVersioning,
    BuildInfoPlugin
  )
  .settings(
    name := "chuti-shared",
  )


lazy val server: Project = project.in(file("server"))
  .dependsOn(shared)
  .settings(commonSettings)
  .enablePlugins(
    AutomateHeaderPlugin,
    GitVersioning
  )
  .settings(
    name := "chuti-server",
    libraryDependencies ++= Seq(
      "com.github.pathikrit" %% "better-files" % "3.8.0" withSources(),
      "de.heikoseeberger" %% "akka-http-circe" % "1.31.0" withSources(),
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion withSources(),
      "com.typesafe.akka" %% "akka-stream" % akkaVersion withSources(),
      "com.github.daddykotex" %% "courier" % "2.0.0" withSources(),
      "com.typesafe.akka" %% "akka-http" % "10.1.11" withSources(),
      "dev.zio" %% "zio" % "1.0.0-RC18-2" withSources(),
      "com.github.ghostdogpr" %% "caliban" % "0.7.2" withSources(),
      "ch.qos.logback" % "logback-classic" % "1.2.3" withSources()
    )
  )

lazy val web: Project = project.in(file("web"))
  .dependsOn(shared)
  .settings(commonSettings)
  .enablePlugins(
    AutomateHeaderPlugin,
    GitVersioning
  )
  .settings(
    name := "chuti-web"
  )
