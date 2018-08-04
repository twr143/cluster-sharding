import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
val akkaVersion = "2.5.13"

val project = Project(
  id = "cluster-sharding",
  base = file("."),
  settings = Defaults.coreDefaultSettings ++ SbtMultiJvm.multiJvmSettings ++ Seq(
    name := "cluster-sharding",
    version := "0.2",
    scalaVersion := "2.12.6",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
      "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "org.scalatest" %% "scalatest" % "3.0.5" % "test",
      "commons-io" % "commons-io" % "2.4" % "test",
      "org.postgresql" % "postgresql" % "42.2.2",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "org.json4s" %% "json4s-jackson" % "3.5.3",
      "com.github.tminglei" %% "slick-pg" % "0.16.2",
      "com.github.romix.akka" %% "akka-kryo-serialization" % "0.5.2",
      "com.github.dnvriend" %% "akka-persistence-jdbc" % "3.4.0"),
    mainClass in(Compile, run) := Some("sample.blog.BlogApp"),
    // make sure that MultiJvm test are compiled by the default test compilation
    compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test),
    // disable parallel tests
    parallelExecution in Test := false,
    // make sure that MultiJvm tests are executed by the default test target, 
    // and combine the results from ordinary test and multi-jvm tests
    executeTests in Test <<= (executeTests in Test, executeTests in MultiJvm) map {
      case (testResults, multiNodeResults) =>
        val overall =
          if (testResults.overall.id < multiNodeResults.overall.id)
            multiNodeResults.overall
          else
            testResults.overall
        Tests.Output(overall,
          testResults.events ++ multiNodeResults.events,
          testResults.summaries ++ multiNodeResults.summaries)
    }
  )
) configs MultiJvm
