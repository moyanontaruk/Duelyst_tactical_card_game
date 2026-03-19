error id: file://<HOME>/Documents/GitHub/IT+%20Group%20Project/MSc-IT-plus-2026-LB01-T/build.sbt:
file://<HOME>/Documents/GitHub/IT+%20Group%20Project/MSc-IT-plus-2026-LB01-T/build.sbt
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -dependencyOverrides.
	 -dependencyOverrides#
	 -dependencyOverrides().
	 -scala/Predef.dependencyOverrides.
	 -scala/Predef.dependencyOverrides#
	 -scala/Predef.dependencyOverrides().
offset: 838
uri: file://<HOME>/Documents/GitHub/IT+%20Group%20Project/MSc-IT-plus-2026-LB01-T/build.sbt
text:
```scala
lazy val root = (project in file("."))
  .enablePlugins(PlayJava)
  .settings(

    //will delete before turning in just need this line to run on mac - Hope
    PlayKeys.fileWatchService := play.dev.filewatch.FileWatchService.polling(2000),


    name := "ITSD Card Game 25-26",
    version := "1.1",
    scalaVersion := "2.13.1",
    // https://github.com/sbt/junit-interface
    testOptions += Tests.Argument(TestFrameworks.JUnit, "-a", "-v"),
    libraryDependencies ++= Seq(
      guice,
      ws,
      "org.webjars" %% "webjars-play" % "2.8.0",
      "org.webjars" % "bootstrap" % "2.3.2",
      "org.webjars" % "flot" % "0.8.3",

      // Testing libraries for dealing with CompletionStage...
      "org.assertj" % "assertj-core" % "3.14.0" % Test,
      "org.awaitility" % "awaitility" % "4.0.1" % Test,
    ),
    dependencyOverr@@ides += "commons-codec" % "commons-codec" % "1.6",
    dependencyOverrides += "commons-io" % "commons-io" % "2.1",
    libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.10.3",
    libraryDependencies += "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % "2.10.3",
    libraryDependencies += "junit" % "junit" % "4.13.2",
    libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % Test exclude("junit", "junit-dep"),
    LessKeys.compress := true,
    javacOptions ++= Seq(
      "--release", "11",
      "-Xlint:unchecked",
      "-Xlint:deprecation"
    )
  )

```


#### Short summary: 

empty definition using pc, found symbol in pc: 