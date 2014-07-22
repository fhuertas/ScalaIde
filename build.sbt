name := """ScalaIde"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.10.2"

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  ws,
  "com.typesafe.akka" % "akka-actor_2.10" % "2.3.1",
  "org.scala-lang" % "scala-compiler" % "2.10.2",
  "org.scala-lang" % "scala-library" % "2.10.2",
  "org.scala-lang" % "scala-reflect" % "2.10.2"
)


