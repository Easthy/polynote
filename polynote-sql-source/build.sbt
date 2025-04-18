import java.io.File
import scala.util.Try

name := "polynote"

val buildUI: TaskKey[Unit] = taskKey[Unit]("Building UI...")
val distUI: TaskKey[Unit] = taskKey[Unit]("Building UI for distribution...")
val runAssembly: TaskKey[Unit] = taskKey[Unit]("Running spark server from assembly...")
val distFiles: TaskKey[Seq[File]] = taskKey[Seq[File]]("Distribution files")
val prepDistFiles: TaskKey[Seq[File]] = taskKey[Seq[File]]("Prepare distribution files")
val dependencyJars: TaskKey[Seq[(File, String)]] = taskKey("Dependency JARs which aren't included in the assembly")
val polynoteJars: TaskKey[Seq[(File, String)]] = taskKey("Polynote JARs")
val sparkVersion: SettingKey[String] = settingKey("Spark version")
val circeVersion: SettingKey[String] = settingKey("circe version")
val circeYamlVersion: SettingKey[String] = settingKey("circe-yaml version")
val sparkInstallLocation: SettingKey[String] = settingKey("Location of Spark installation(s)")
val sparkHome: SettingKey[String] = settingKey("Location of specific Spark installation to use for SPARK_HOME during tests")


val versions = new {
  val coursier   = "2.0.0-RC5-6"
  val zio        = "1.0.11"
  val javaparser = "3.25.5"
}


lazy val nativeLibraryPath = {
  val jepPath = sys.env.get("JEP_DIR") orElse Try {
    import sys.process._
    Seq("bash", "-c", "pip3 show jep |grep ^Location |cut -d' ' -f2")
      .lineStream.toList
      .headOption.map(_.trim)
      .filterNot(_.isEmpty)
      .map(_ + "/jep")
  }.toOption.flatten orElse
    sys.env.get("JAVA_LIBRARY_PATH") orElse
    sys.env.get("LD_LIBRARY_PATH") orElse
    sys.env.get("DYLD_LIBRARY_PATH") getOrElse "."

  Seq(jepPath, ".").mkString(File.pathSeparator)
}

val distBuildDir = file(".") / "target" / "dist" / "polynote"
val scalaVersions = Seq("2.12.15", "2.13.6")
lazy val scalaBinaryVersions = scalaVersions.map {
  ver => ver.split('.').take(2).mkString(".")
}.distinct

val shapelessVersion = Map("2.12" -> "2.3.2", "2.13" -> "2.3.3")

val commonSettings = Seq(
  scalaVersion := "2.12.15",
  crossScalaVersions := scalaVersions,
  organization := "org.polynote",
  publishMavenStyle := true,
  homepage := Some(url("https://polynote.org")),
  licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/polynote/polynote"),
      "scm:git@github.com:polynote/polynote.git"
    )
  ),
  version := "0.6.1",
  dependencyOverrides += "com.chuusai" %% "shapeless" % shapelessVersion(scalaBinaryVersion.value),
  publishTo := sonatypePublishToBundle.value,
  // disable scalaDoc generation because it's causing weird compiler errors and we don't use it anyways
  Compile / packageDoc / publishArtifact := false,
  developers := List(
    Developer(id = "jeremyrsmith", name = "Jeremy Smith", email = "", url = url("https://github.com/jeremyrsmith")),
    Developer(id = "jonathanindig", name = "Jonathan Indig", email = "", url = url("https://github.com/jonathanindig")),
    Developer(id = "omidmogasemi", name = "Omid Mogasemi", email = "", url = url("https://github.com/omidmogasemi"))
  ),
  javacOptions ++= Seq("-source", "8", "-target", "8"),
  scalacOptions ++= Seq(
    "-language:higherKinds",
    "-unchecked",
    "-target:jvm-1.8",
    "-release", "8"
  ) ++ (if (scalaBinaryVersion.value.startsWith("2.13")) Nil else Seq("-Ypartial-unification")),
  Test / fork := true,
  Test / javaOptions += s"-Djava.library.path=$nativeLibraryPath",
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.0.8" % "test",
    "org.scalacheck" %% "scalacheck" % "1.14.0" % "test"
  ),
  assembly / assemblyMergeStrategy := {
    case PathList("META-INF", "CHANGES") => MergeStrategy.discard
    case PathList("coursier", "shaded", xs @ _*) => MergeStrategy.first // coursier shades some of the same classes. assembly somehow can't dedupe even though they seem identical to me.
    case PathList(_, "BuildInfo$.class") => MergeStrategy.discard
    case x if x.endsWith("module-info.class") => MergeStrategy.discard
    case x =>
      val oldStrategy = (assembly / assemblyMergeStrategy).value
      oldStrategy(x)
  },
  assembly / assemblyOption := {
    (assembly / assemblyOption).value.withIncludeScala(false)
  },
  Global / cancelable := true,
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
  buildUI := {
    sys.process.Process(Seq("npm", "run", "build"), file("./polynote-frontend/")) ! streams.value.log
  },
  distUI := {
    sys.process.Process(Seq("npm", "run", "clean"), file("./polynote-frontend/")) ! streams.value.log
    sys.process.Process(Seq("npm", "run", "dist"), file("./polynote-frontend/")) ! streams.value.log
  },
  distFiles := Nil,
  prepDistFiles := {
    val targetDir = distBuildDir / "deps" / scalaBinaryVersion.value
    targetDir.mkdirs()
    val sourceFiles = distFiles.value
    val destFiles = sourceFiles.map {
      file => targetDir / file.name
    }
    IO.copy(sourceFiles zip destFiles, overwrite = true, preserveLastModified = true, preserveExecutable = true)
    destFiles
  },
  scalacOptions += "-deprecation",
  assembly / test := {},
  circeVersion := "0.14.3",
  circeYamlVersion := "0.15.2"
)

lazy val `polynote-macros` = project.settings(
  commonSettings,
  libraryDependencies ++= Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided"),
  scalacOptions ++= Seq(
    "-language:experimental.macros"
  )
)

lazy val `polynote-runtime` = project.settings(
  commonSettings,
  scalacOptions ++= Seq(
    "-language:experimental.macros"
  ),
  libraryDependencies ++= Seq(
    "black.ninia" % "jep" % "4.0.0",
    "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided"
  ),
  distFiles := Seq(assembly.value)
).enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      scalaBinaryVersion,
      BuildInfoKey.action("commit") {
        git.gitHeadCommit.value.getOrElse("unknown")
      },
      BuildInfoKey.action("buildTime") {
        System.currentTimeMillis
      }
    ),
    buildInfoPackage := "polynote.buildinfo"
  ).dependsOn(`polynote-macros`)


val `polynote-env` = project.settings(
  commonSettings,
  scalacOptions += "-language:experimental.macros",
  libraryDependencies ++= Seq(
    //"dev.zio" %% "zio-interop-cats" % versions.zioInterop % "provided",
    "dev.zio" %% "zio" % versions.zio,
    "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided"
  )
)

val `polynote-kernel` = project.settings(
  commonSettings,
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided",
    "org.scala-lang" % "scala-compiler" % scalaVersion.value % "test",
    "org.scalameta" % "semanticdb-scalac-core" % "4.6.0" cross CrossVersion.full,
    "dev.zio" %% "zio" % versions.zio,
    "dev.zio" %% "zio-streams" % versions.zio,
    "org.scodec" %% "scodec-core" % "1.11.4",
    "io.get-coursier" %% "coursier" % versions.coursier,
    "io.get-coursier" %% "coursier-cache" % versions.coursier,
    "io.github.classgraph" % "classgraph" % "4.8.47",
    "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.1",
    "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.0",
    "io.circe" %% "circe-yaml" % circeYamlVersion.value,
    "io.circe" %% "circe-generic" % circeVersion.value,
    "io.circe" %% "circe-generic-extras" % circeVersion.value,
    "io.circe" %% "circe-parser" % circeVersion.value,
    "net.sf.py4j" % "py4j" % "0.10.7",
    "com.github.javaparser" % "javaparser-core" % versions.javaparser,
    "com.github.javaparser" % "javaparser-symbol-solver-core" % versions.javaparser,
    "org.postgresql" % "postgresql" % "42.6.0",
    "com.amazon.redshift" % "redshift-jdbc42" % "2.1.0.9",
    "org.scalamock" %% "scalamock" % "4.4.0" % "test"
  ),
  distFiles := Seq(assembly.value) ++ (Compile / dependencyClasspath).value.collect {
    case jar if jar.data.name.matches(".*scala-(library|reflect|compiler|collection-compat|xml).*") => jar.data
  },
  coverageExcludedPackages := "polynote\\.kernel\\.interpreter\\.python\\..*;polynote\\.runtime\\.python\\..*" // see https://github.com/scoverage/scalac-scoverage-plugin/issues/176
).dependsOn(`polynote-runtime` % "provided", `polynote-runtime` % "test", `polynote-env`)

val `polynote-server` = project.settings(
  commonSettings,
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided",
    "org.polynote" %% "uzhttp" % "0.2.8",
    "com.vladsch.flexmark" % "flexmark" % "0.34.32",
    "com.vladsch.flexmark" % "flexmark-ext-yaml-front-matter" % "0.34.32",
    "org.slf4j" % "slf4j-simple" % "1.7.25"
  ),
  //Compile / unmanagedResourceDirectories += (ThisBuild / baseDirectory).value / "polynote-frontend" / "dist",
  packageBin := {
    val _ = distUI.value
    (Compile / packageBin).value
  },
  distFiles := Seq(assembly.value),
  Test / testOptions += Tests.Argument("-oF")
).dependsOn(`polynote-runtime` % "provided", `polynote-runtime` % "test", `polynote-kernel` % "provided", `polynote-kernel` % "test->test")

val sparkVersions = Map(
  "2.12" -> "3.1.2",
  "2.13" -> "3.2.1"
)

// keep expected checksums here. This has two benefits over checking the sha512sum from the archive:
// 1. We'll know if anything changes in the archive
// 2. Spark's checksums are generated with gpg rather than sha512sum up until a certain version, so they're a pain to verify
//    See https://issues.apache.org/jira/browse/SPARK-30683
// To add to this list, download the tarball for the new version from the apache repo and run `sha512sum <file>.tgz`
val sparkChecksums = Map(
  "3.1.2" -> "ba47e074b2a641b23ee900d4e28260baa250e2410859d481b38f2ead888c30daea3683f505608870148cf40f76c357222a2773f1471e7342c622e93bf02479b7",
  "3.2.1" -> "2ec9f1cb65af5ee7657ca83a1abaca805612b8b3a1d8d9bb67e317106025c81ba8d44d82ad6fdb45bbe6caa768d449cd6a4945ec050ce9390f806f46c5cb1397"
)

val sparkDistUrl: String => String =
  ver => s"https://archive.apache.org/dist/spark/spark-$ver/"

val sparkSettings = Seq(
  resolvers ++= {
    Seq(MavenRepository(name = "Apache Staging", root = "https://repository.apache.org/content/repositories/staging"))
  },
  sparkVersion := sparkVersions(scalaBinaryVersion.value),
  libraryDependencies ++= Seq(
    "org.apache.spark" %% "spark-sql" % sparkVersion.value % "provided",
    "org.apache.spark" %% "spark-repl" % sparkVersion.value % "provided",
    "org.apache.spark" %% "spark-sql" % sparkVersion.value % "test",
    "org.apache.spark" %% "spark-repl" % sparkVersion.value % "test"
  ),
  sparkInstallLocation := {
    sys.env.get("SPARK_INSTALL_LOCATION")
      .getOrElse((file(".").getAbsoluteFile / "target" / "spark").getCanonicalPath)
  },
  sparkHome := {
    (file(sparkInstallLocation.value) / s"spark-${sparkVersion.value}-bin-hadoop2.7").toString
  },
  Test / testOptions += Tests.Setup { () =>
    import sys.process._
    val baseDir = file(sparkInstallLocation.value)
    val distVersion = sparkVersion.value
    val pkgName = if (scalaBinaryVersion.value == "2.13") s"spark-$distVersion-bin-hadoop2.7-scala2.13" else s"spark-$distVersion-bin-hadoop2.7"
    val filename = s"$pkgName.tgz"
    val distUrl = url(s"${sparkDistUrl(distVersion)}/$filename")
    val destDir = baseDir / pkgName
    // It seems that this Tests.Setup block gets run concurrently, which can sometimes cause weirdness to happen.
    // So we try to use a lockfile to ensure that it only ever runs once
    // (Yes there still the possibility of a race condition here, but I don't know how to properly synchronize SBT tasks...)
    val lockFile = baseDir / s"spark_${scalaBinaryVersion.value}_test_setup_is_running.lock"
    if (lockFile.exists()) {
      println(s"Lock file $lockFile exists, test setup is already running. Waiting for it to finish...")
      val start = System.currentTimeMillis()
      val timeout = 10 * 60 * 1000 // 10 minutes
      val checkInterval = 10 * 1000 // 10 seconds
      while (System.currentTimeMillis() < start + timeout && lockFile.exists()) {
        println(s"Lock file $lockFile still exists after ${System.currentTimeMillis() - start}ms. Waiting...")
        Thread.sleep(checkInterval)
      }
      if (lockFile.exists()) {
        throw new Exception(s"Lock file $lockFile still exists after $timeout ms. Aborting.")
      } else {
        println("Lock file no longer exists, test setup must have finished")
      }
    } else {
      baseDir.mkdirs()
      lockFile.createNewFile()
      lockFile.deleteOnExit()

      try {
        if (destDir.exists()) {
          println(s"$destDir already exists, skipping download and extract")
        } else {
          val pkgFile = baseDir / filename
          if (!pkgFile.exists()) {
            pkgFile.createNewFile()
            println(s"Downloading $distUrl to $pkgFile...")
            (distUrl #> pkgFile).!!
          }

          println(s"Verifying checksum for $pkgFile for $distVersion...")
          val expectedChecksum = sparkChecksums(distVersion)
          val actualChecksum = Seq("sha512sum", pkgFile.toString).!!.trim.split(" ").head
          if (actualChecksum == expectedChecksum) {
            println(s"Checksum verified for $pkgFile for $distVersion")
          } else {
            throw new Exception(s"Checksum mismatch for $pkgFile for $distVersion. Expected:\n$expectedChecksum\nGot:\n$actualChecksum")
          }

          println(s"Extracting $pkgFile to $baseDir")
          println(Seq("tar", "-zxpf", pkgFile.toString, "-C", baseDir.toString).!!)
        }
      } finally {
        lockFile.delete()
      }
    }

    println("Test setup completed")
  },
  Test / envVars ++= {
    Map(
      "SPARK_HOME" -> sparkHome.value,
      "PATH" -> Seq(sparkHome, sys.env("PATH")).mkString(File.pathSeparator)
    )
  }
)

lazy val `polynote-spark-runtime` = project.settings(
  commonSettings,
  sparkSettings,
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided"
  ),
  distFiles := Seq(assembly.value)
).dependsOn(`polynote-runtime` % "provided")

lazy val `polynote-spark` = project.settings(
  commonSettings,
  sparkSettings,
  Test / testOptions += Tests.Argument("-oF"),
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided"
  ),
  assembly / assemblyOption := {
    (assembly / assemblyOption).value
      .withIncludeScala(false)
      .withPrependShellScript(Some(
        IO.read(file(".") / "scripts/polynote").linesIterator.toSeq
      ))
  },
  distFiles := Seq(assembly.value)
) dependsOn (
  `polynote-kernel` % "provided",
  `polynote-kernel` % "test->test",
  `polynote-server` % "provided",
  `polynote-spark-runtime` % "provided",
  `polynote-spark-runtime` % "test",
  `polynote-runtime` % "provided",
  `polynote-runtime` % "test")

def waitForCommand(command: String): State => State = { st =>
  // there *has* to be a better way to run a command and wait for it to finish...
  var nextState = Command.process(command, st.copy(remainingCommands = Nil))
  while (nextState.remainingCommands.nonEmpty) {
    nextState = Command.process(nextState.remainingCommands.head.commandLine, nextState.copy(remainingCommands = nextState.remainingCommands.tail))
  }
  nextState.copy(remainingCommands = st.remainingCommands)
}

val dist = Command.command(
  "dist",
  "Perform cross-build and build distribution archive",
  "Performs cross-build and builds a distribution archive in target/polynote-dist.tar.gz"
) {
  state =>
    val resultState = waitForCommand("+prepDistFiles")(state)
    val examples = IO.listFiles(file(".") / "docs" / "examples").map(f => (f, s"polynote/examples/${f.getName}"))
    val baseDir = file(".")
    val targetDir = baseDir / "target"
    val tarFile = targetDir / "polynote-dist.tar"
    val outFile = targetDir / "polynote-dist.tar.gz"

    if (tarFile.exists())
      tarFile.delete()

    if(outFile.exists())
      outFile.delete()

    val files = examples ++ List(
      (file(".") / "config-template.yml") -> "polynote/config-template.yml",
      (file(".") / "requirements.txt") -> "polynote/requirements.txt",
      (file(".") / "scripts" / "plugin") -> "polynote/plugin",
      (file(".") / "scripts" / "polynote.py") -> "polynote/polynote.py")

    val distDir = targetDir / "dist"
    val resolvedFiles = files.map {
      case (srcFile, targetPath) => srcFile -> (distDir / targetPath)
    }

    scalaBinaryVersions.foreach {
      binaryVersion =>
        (distDir / "polynote" / "plugins" / binaryVersion).mkdirs()
        (distDir / "polynote" / "plugins.d" / binaryVersion).mkdirs()
    }

    IO.copy(resolvedFiles, overwrite = true, preserveLastModified = true, preserveExecutable = true)

    IO.copyDirectory(file(".") / "polynote-frontend" / "dist" / "static", distDir / "polynote" / "static")

    import sys.process.stringSeqToProcess
    println("Making archive")
    Seq("tar", "-cf", tarFile.toString, "-C", distDir.toString, "polynote").!
    Seq("gzip", "-S", ".gz", tarFile.toString).!
    resultState
}

lazy val polynote = project.in(file(".")).aggregate(`polynote-env`, `polynote-runtime`, `polynote-spark-runtime`, `polynote-kernel`, `polynote-server`, `polynote-spark`)
    .settings(
      commonSettings,
      commands ++= Seq(dist)
    )

