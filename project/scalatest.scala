import sbt._
import Keys._
import java.net.{URL, URLClassLoader}
import java.io.PrintWriter
import scala.io.Source
import com.typesafe.sbt.osgi.SbtOsgi._
import com.typesafe.sbt.SbtPgp._

object ScalatestBuild extends Build {

  val scalaVersionToUse = "2.10.3"
  val releaseVersion = "2.2.0-SNAPSHOT"
  val githubTag = "release-2.1.0-RC2-for-scala-2.10" // for scaladoc source urls

  val docSourceUrl =
    "https://github.com/scalatest/scalatest/tree/"+ githubTag +
    "/src/main/scala€{FILE_PATH}.scala"

  def envVar(name: String): Option[String] =
    try {
      Some(sys.env(name))
    }
    catch {
      case e: NoSuchElementException => None
    }

  def getGPGFilePath: String =
    envVar("SCALATEST_GPG_FILE") match {
      case Some(path) => path
      case None => (Path.userHome / ".gnupg" / "secring.gpg").getAbsolutePath
    }

  def getGPGPassphase: Option[Array[Char]] =
    envVar("SCALATEST_GPG_PASSPHASE") match {
      case Some(passphase) => Some(passphase.toCharArray)
      case None => None
    }

  def getNexusCredentials: Credentials =
    (envVar("SCALATEST_NEXUS_LOGIN"), envVar("SCALATEST_NEXUS_PASSWORD")) match {
      case (Some(login), Some(password)) => Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", login, password)
      case _ => Credentials(Path.userHome / ".ivy2" / ".credentials")
    }

  lazy val scalatest = Project("scalatest", file("."))
   .settings(
     projectTitle := "ScalaTest",
     organization := "org.scalatest",
     version := releaseVersion,
     scalaVersion := scalaVersionToUse,
     scalacOptions ++= Seq("-no-specialization", "-feature", "-target:jvm-1.5"),
     initialCommands in console := """|import org.scalatest._
                                      |import org.scalautils._
                                      |import Matchers._""".stripMargin,
     ivyXML := 
       <dependency org="org.eclipse.jetty.orbit" name="javax.servlet" rev="3.0.0.v201112011016">
         <artifact name="javax.servlet" type="orbit" ext="jar"/>
       </dependency>, 
     libraryDependencies ++= scalatestDependencies,
     libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersionToUse, // this is needed to compile macro
     resolvers += "Sonatype Public" at "https://oss.sonatype.org/content/groups/public",
     publishTo <<= version { v: String =>
       val nexus = "https://oss.sonatype.org/"
       if (v.trim.endsWith("SNAPSHOT")) Some("publish-snapshots" at nexus + "content/repositories/snapshots")
       else                             Some("publish-releases" at nexus + "service/local/staging/deploy/maven2")
     },
     publishMavenStyle := true,
     publishArtifact in Test := false,
     pomIncludeRepository := { _ => false },
     pomExtra := (
       <url>http://www.scalatest.org</url>
         <licenses>
           <license>
             <name>the Apache License, ASL Version 2.0</name>
             <url>http://www.apache.org/licenses/LICENSE-2.0</url>
             <distribution>repo</distribution>
           </license>
         </licenses>
         <scm>
           <url>https://github.com/scalatest/scalatest</url>
           <connection>scm:git:git@github.com:scalatest/scalatest.git</connection>
           <developerConnection>
             scm:git:git@github.com:scalatest/scalatest.git
           </developerConnection>
         </scm>
         <developers>
           <developer>
             <id>bvenners</id>
             <name>Bill Venners</name>
             <email>bill@artima.com</email>
           </developer>
           <developer>
             <id>gcberger</id>
             <name>George Berger</name>
             <email>george.berger@gmail.com</email>
           </developer>
           <developer>
             <id>cheeseng</id>
             <name>Chua Chee Seng</name>
             <email>cheeseng@amaseng.com</email>
           </developer>
         </developers>
      ),
     credentials += getNexusCredentials,
     pgpSecretRing := file(getGPGFilePath),
     pgpPassphrase := getGPGPassphase,
     genMustMatchersTask,
     genGenTask, 
     genTablesTask, 
     genCodeTask, 
     genFactoriesTask,
     genCompatibleClassesTask,
     sourceGenerators in Compile <+= 
         (baseDirectory, sourceManaged in Compile) map genFiles("gengen", "GenGen.scala")(GenGen.genMain),
     sourceGenerators in Compile <+= 
         (baseDirectory, sourceManaged in Compile) map genFiles("gentables", "GenTable.scala")(GenTable.genMain),
     sourceGenerators in Compile <+=
         (baseDirectory, sourceManaged in Compile) map genFiles("genmatchers", "MustMatchers.scala")(GenMatchers.genMain),
     sourceGenerators in Compile <+= 
         (baseDirectory, sourceManaged in Compile) map genFiles("genfactories", "GenFactories.scala")(GenFactories.genMain),
     sourceGenerators in Compile <+=
         (baseDirectory, sourceManaged in Compile) map genFiles("gencompcls", "GenCompatibleClasses.scala")(GenCompatibleClasses.genMain),
     testOptions in Test := Seq(Tests.Argument("-l", "org.scalatest.tags.Slow", 
                                               "-m", "org.scalatest", 
                                               "-m", "org.scalautils",
                                               "-m", "org.scalatest.fixture", 
                                               "-m", "org.scalatest.concurrent", 
                                               "-m", "org.scalatest.testng", 
                                               "-m", "org.scalatest.junit", 
                                               "-m", "org.scalatest.events", 
                                               "-m", "org.scalatest.prop", 
                                               "-m", "org.scalatest.tools", 
                                               "-m", "org.scalatest.matchers", 
                                               "-m", "org.scalatest.suiteprop", 
                                               "-m", "org.scalatest.mock", 
                                               "-m", "org.scalatest.path", 
                                               "-m", "org.scalatest.selenium", 
                                               "-m", "org.scalatest.exceptions", 
                                               "-m", "org.scalatest.time", 
                                               "-m", "org.scalatest.words", 
                                               "-m", "org.scalatest.enablers", 
                                               "-oDI", 
                                               "-h", "target/html", 
                                               "-u", "target/junit",
                                               "-fW", "target/result.txt")),
     docsrcDirSetting,
     docSourcesSetting,
     docScalacOptionsSetting,
     scalatestDocTaskSetting
   ).settings(osgiSettings: _*).settings(
      OsgiKeys.exportPackage := Seq(
        "org.scalatest",
        "org.scalatest.concurrent",
        "org.scalatest.enablers",
        "org.scalatest.events",
        "org.scalatest.exceptions",
        "org.scalatest.fixture",
        "org.scalatest.junit",
        "org.scalatest.matchers",
        "org.scalatest.mock",
        "org.scalatest.path",
        "org.scalatest.prop",
        "org.scalatest.selenium",
        "org.scalatest.tags",
        "org.scalatest.tagobjects",
        "org.scalatest.testng",
        "org.scalatest.time",
        "org.scalatest.tools",
        "org.scalatest.verb",
        "org.scalatest.words",
        "org.scalautils"
      ),
      OsgiKeys.additionalHeaders:= Map(
        "Bundle-Name" -> "ScalaTest",
        "Bundle-Description" -> "ScalaTest is an open-source test framework for the Java Platform designed to increase your productivity by letting you write fewer lines of test code that more clearly reveal your intent.",
        "Bundle-DocURL" -> "http://www.scalatest.org/",
        "Bundle-Vendor" -> "Artima, Inc.",
        "Main-Class" -> "org.scalatest.tools.Runner"
      )
   )

  lazy val scalautils = Project("scalautils", file("genscalautils"))
    .settings(
      projectTitle := "ScalaUtils",
      organization := "org.scalautils",
      version := releaseVersion,
      scalaVersion := scalaVersionToUse,
      scalacOptions ++= Seq("-no-specialization", "-feature", "-target:jvm-1.5"),
      initialCommands in console := "import org.scalautils._",
      libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersionToUse, // this is needed to compile macro
      resolvers += "Sonatype Public" at "https://oss.sonatype.org/content/groups/public",
      publishTo <<= version { v: String =>
        val nexus = "https://oss.sonatype.org/"
        if (v.trim.endsWith("SNAPSHOT")) Some("publish-snapshots" at nexus + "content/repositories/snapshots")
        else                             Some("publish-releases" at nexus + "service/local/staging/deploy/maven2")
      },
      publishMavenStyle := true,
      publishArtifact in Test := false,
      pomIncludeRepository := { _ => false },
      pomExtra := (
        <url>http://www.scalatest.org</url>
          <licenses>
            <license>
              <name>the Apache License, ASL Version 2.0</name>
              <url>http://www.apache.org/licenses/LICENSE-2.0</url>
              <distribution>repo</distribution>
            </license>
          </licenses>
          <scm>
            <url>https://github.com/scalatest/scalatest</url>
            <connection>scm:git:git@github.com:scalatest/scalatest.git</connection>
            <developerConnection>
              scm:git:git@github.com:scalatest/scalatest.git
            </developerConnection>
          </scm>
          <developers>
            <developer>
              <id>bvenners</id>
              <name>Bill Venners</name>
              <email>bill@artima.com</email>
            </developer>
            <developer>
              <id>gcberger</id>
              <name>George Berger</name>
              <email>george.berger@gmail.com</email>
            </developer>
            <developer>
              <id>cheeseng</id>
              <name>Chua Chee Seng</name>
              <email>cheeseng@amaseng.com</email>
            </developer>
          </developers>
        ),
      credentials += getNexusCredentials,
      pgpSecretRing := file(getGPGFilePath),
      pgpPassphrase := getGPGPassphase,
      sourceGenerators in Compile <+=
        (baseDirectory, sourceManaged in Compile) map genFiles("", "GenScalaUtils.scala")(GenScalaUtils.genMain),
      sourceGenerators in Test <+=
        (baseDirectory, sourceManaged in Test) map genFiles("", "GenScalaUtils.scala")(GenScalaUtils.genTest),
      docsrcDirSetting,
      docSourcesSetting,
      docScalacOptionsSetting,
      scalautilsDocTaskSetting
    ).settings(osgiSettings: _*).settings(
      OsgiKeys.exportPackage := Seq(
        "org.scalautils"
      ),
      OsgiKeys.additionalHeaders:= Map(
        "Bundle-Name" -> "ScalaUtils",
        "Bundle-Description" -> "ScalaUtils is an open-source library for Scala projects.",
        "Bundle-DocURL" -> "http://www.scalautils.org/",
        "Bundle-Vendor" -> "Artima, Inc."
      )
    ).dependsOn(scalatest  % "test->test")

  lazy val gentests = Project("gentests", file("gentests"))
   .settings(
     organization := "org.scalatest",
     version := releaseVersion,
     scalaVersion := scalaVersionToUse,
     scalacOptions ++= Seq("-no-specialization", "-feature"),
     libraryDependencies ++= scalatestDependencies,
     resolvers += "Sonatype Public" at "https://oss.sonatype.org/content/groups/public",
     genTestsHelperTask,
     genMustMatchersTask, 
     genGenTask, 
     genTablesTask, 
     genInspectorsTask,
     genInspectorsShorthandsTask,
     genTheyWordTask,
     genContainTask, 
     genSortedTask, 
     genLoneElementTask, 
     genEmptyTask,
     sourceGenerators in Test <+=
       (baseDirectory, sourceManaged in Test) map genFiles("gentestshelper", "GenTestsHelper.scala")(GenTestsHelper.genTest),
     sourceGenerators in Test <+= 
       (baseDirectory, sourceManaged in Test) map genFiles("gengen", "GenGen.scala")(GenGen.genTest),
     sourceGenerators in Test <+= 
       (baseDirectory, sourceManaged in Test) map genFiles("gentables", "GenTable.scala")(GenTable.genTest),
     sourceGenerators in Test <+=
       (baseDirectory, sourceManaged in Test) map genFiles("genmatchers", "GenMatchers.scala")(GenMatchers.genTest),
     sourceGenerators in Test <+= 
       (baseDirectory, sourceManaged in Test) map genFiles("genthey", "GenTheyWord.scala")(GenTheyWord.genTest),
     sourceGenerators in Test <+= 
       (baseDirectory, sourceManaged in Test) map genFiles("geninspectors", "GenInspectors.scala")(GenInspectors.genTest),
     sourceGenerators in Test <+=
       (baseDirectory, sourceManaged in Test) map genFiles("geninspectorsshorthands", "GenInspectorsShorthands.scala")(GenInspectorsShorthands.genTest),
     sourceGenerators in Test <+= 
       (baseDirectory, sourceManaged in Test) map genFiles("gencontain", "GenContain.scala")(GenContain.genTest),
     sourceGenerators in Test <+= 
       (baseDirectory, sourceManaged in Test) map genFiles("gensorted", "GenSorted.scala")(GenSorted.genTest),
     sourceGenerators in Test <+=
       (baseDirectory, sourceManaged in Test) map genFiles("genloneelement", "GenLoneElement.scala")(GenLoneElement.genTest),
     sourceGenerators in Test <+=
       (baseDirectory, sourceManaged in Test) map genFiles("genempty", "GenEmpty.scala")(GenEmpty.genTest),
     testOptions in Test := Seq(Tests.Argument("-l", "org.scalatest.tags.Slow", 
                                               "-oDI", 
                                               "-h", "gentests/target/html", 
                                               "-u", "gentests/target/junit", 
                                               "-fW", "target/result.txt"))
   ).dependsOn(scalatest)

   def scalatestDependencies = Seq(
     "org.scala-sbt" % "test-interface" % "1.0" % "optional",
     "org.scalacheck" %% "scalacheck" % "1.11.0" % "optional",
     "org.easymock" % "easymockclassextension" % "3.1" % "optional", 
     "org.jmock" % "jmock-legacy" % "2.5.1" % "optional", 
     "org.mockito" % "mockito-all" % "1.9.0" % "optional", 
     "org.testng" % "testng" % "6.8.7" % "optional",
     "com.google.inject" % "guice" % "2.0" % "optional",
     "junit" % "junit" % "4.10" % "optional", 
     "org.seleniumhq.selenium" % "selenium-java" % "2.35.0" % "optional",
     "org.apache.ant" % "ant" % "1.7.1" % "optional",
     "commons-io" % "commons-io" % "1.3.2" % "test", 
     "org.eclipse.jetty" % "jetty-server" % "8.1.8.v20121106" % "test", 
     "org.eclipse.jetty" % "jetty-webapp" % "8.1.8.v20121106" % "test",
     "org.ow2.asm" % "asm-all" % "4.1" % "optional",
     "org.pegdown" % "pegdown" % "1.4.2" % "optional"
  )

  def genFiles(name: String, generatorSource: String)(gen: (File, String) => Unit)(basedir: File, outDir: File): Seq[File] = {
    val tdir = outDir / "scala" / name
    val genSource = basedir / "project" / generatorSource
    def results = (tdir ** "*.scala").get
    if (results.isEmpty || results.exists(_.lastModified < genSource.lastModified)) {
      tdir.mkdirs()
      gen(tdir, scalaVersionToUse)
    }
    results
  }
  
  val genMustMatchers = TaskKey[Unit]("genmatchers", "Generate Must Matchers")
  val genMustMatchersTask = genMustMatchers <<= (sourceManaged in Compile, sourceManaged in Test, name) map { (mainTargetDir: File, testTargetDir: File, projName: String) =>
    projName match {
      case "scalatest" =>
        GenMatchers.genMain(new File(mainTargetDir, "scala/genmatchers"), scalaVersionToUse)
      case "gentests" =>
        GenMatchers.genTest(new File(testTargetDir, "scala/genmatchers"), scalaVersionToUse)
    }
  }
  
  val genGen = TaskKey[Unit]("gengen", "Generate Property Checks")
  val genGenTask = genGen <<= (sourceManaged in Compile, sourceManaged in Test, name) map { (mainTargetDir: File, testTargetDir: File, projName: String) =>
    projName match {
      case "scalatest" => 
        GenGen.genMain(new File(mainTargetDir, "scala/gengen"), scalaVersionToUse)
      case "gentests" =>
        GenGen.genTest(new File(testTargetDir, "scala/gengen"), scalaVersionToUse)
    }
  }
  
  val genTables = TaskKey[Unit]("gentables", "Generate Tables")
  val genTablesTask = genTables <<= (sourceManaged in Compile, sourceManaged in Test, name) map { (mainTargetDir: File, testTargetDir: File, projName: String) =>
    projName match {
      case "scalatest" => 
        GenTable.genMain(new File(mainTargetDir, "scala/gentables"), scalaVersionToUse)
      case "gentests" => 
        GenTable.genTest(new File(testTargetDir, "scala/gentables"), scalaVersionToUse)
    }
  }
  
  val genTheyWord = TaskKey[Unit]("genthey", "Generate They Word tests")
  val genTheyWordTask = genTheyWord <<= (sourceManaged in Compile, sourceManaged in Test) map { (mainTargetDir: File, testTargetDir: File) =>
    GenTheyWord.genTest(new File(testTargetDir, "scala/genthey"), scalaVersionToUse)
  }
  
  val genInspectors = TaskKey[Unit]("geninspectors", "Generate Inspectors tests")
  val genInspectorsTask = genInspectors <<= (sourceManaged in Compile, sourceManaged in Test) map { (mainTargetDir: File, testTargetDir: File) =>
    GenInspectors.genTest(new File(testTargetDir, "scala/geninspectors"), scalaVersionToUse)
  }

  val genInspectorsShorthands = TaskKey[Unit]("geninspectorsshorthands", "Generate Inspectors Shorthands tests")
  val genInspectorsShorthandsTask = genInspectorsShorthands <<= (sourceManaged in Compile, sourceManaged in Test) map { (mainTargetDir: File, testTargetDir: File) =>
    GenInspectorsShorthands.genTest(new File(testTargetDir, "scala/geninspectorsshorthands"), scalaVersionToUse)
  }
  
  val genFactories = TaskKey[Unit]("genfactories", "Generate Matcher Factories")
  val genFactoriesTask = genFactories <<= (sourceManaged in Compile, sourceManaged in Test) map { (mainTargetDir: File, testTargetDir: File) =>
    GenFactories.genMain(new File(mainTargetDir, "scala/genfactories"), scalaVersionToUse)
  }

  val genCompatibleClasses = TaskKey[Unit]("gencompcls", "Generate Compatible Classes for Java 6 & 7")
  val genCompatibleClassesTask = genCompatibleClasses <<= (sourceManaged in Compile, sourceManaged in Test) map { (mainTargetDir: File, testTargetDir: File) =>
    GenCompatibleClasses.genMain(new File(mainTargetDir, "scala/gencompclass"), scalaVersionToUse)
  }
  
  val genContain = TaskKey[Unit]("gencontain", "Generate contain matcher tests")
  val genContainTask = genContain <<= (sourceManaged in Compile, sourceManaged in Test) map { (mainTargetDir: File, testTargetDir: File) =>
    GenContain.genTest(new File(testTargetDir, "scala/gencontain"), scalaVersionToUse)
  }
  
  val genSorted = TaskKey[Unit]("gensorted", "Generate sorted matcher tests")
  val genSortedTask = genSorted <<= (sourceManaged in Compile, sourceManaged in Test) map { (mainTargetDir: File, testTargetDir: File) =>
    GenSorted.genTest(new File(testTargetDir, "scala/gensorted"), scalaVersionToUse)
  }
  
  val genLoneElement = TaskKey[Unit]("genloneelement", "Generate lone element matcher tests")
  val genLoneElementTask = genLoneElement <<= (sourceManaged in Compile, sourceManaged in Test) map { (mainTargetDir: File, testTargetDir: File) =>
    GenLoneElement.genTest(new File(testTargetDir, "scala/genloneelement"), scalaVersionToUse)
  }
  
  val genEmpty = TaskKey[Unit]("genempty", "Generate empty matcher tests")
  val genEmptyTask = genEmpty <<= (sourceManaged in Compile, sourceManaged in Test) map { (mainTargetDir: File, testTargetDir: File) =>
    GenEmpty.genTest(new File(testTargetDir, "scala/genempty"), scalaVersionToUse)
  }

  val genTestsHelper = TaskKey[Unit]("gentestshelper", "Generate helper classes for gentests project")
  val genTestsHelperTask = genEmpty <<= (sourceManaged in Compile, sourceManaged in Test) map { (mainTargetDir: File, testTargetDir: File) =>
    GenTestsHelper.genTest(new File(testTargetDir, "scala/gentestshelper"), scalaVersionToUse)
  }

  val genCode = TaskKey[Unit]("gencode", "Generate Code, includes Must Matchers and They Word tests.")
  val genCodeTask = genCode <<= (sourceManaged in Compile, sourceManaged in Test) map { (mainTargetDir: File, testTargetDir: File) =>
    GenGen.genMain(new File(mainTargetDir, "scala/gengen"), scalaVersionToUse)
    GenTable.genMain(new File(mainTargetDir, "scala/gentables"), scalaVersionToUse)
    GenMatchers.genMain(new File(mainTargetDir, "scala/genmatchers"), scalaVersionToUse)
    GenFactories.genMain(new File(mainTargetDir, "scala/genfactories"), scalaVersionToUse)
  }

  //
  // Prepares source files for running scaladoc.
  //
  def genDocSources(srcFiles: Seq[File], srcMain: File,
                    managedSrcMain: File, docsrcDir: File): Seq[File] =
  {
    val srcMainScalaDir = srcMain / "scala"
    val managedSrcMainScalaDir = managedSrcMain / "scala"

    val scalaFiles =
      for {
        srcFile <- srcFiles
        if srcFile.name.endsWith(".scala")
      } yield {
        val srcPath = srcFile.getPath
        val docsrcPath =
          srcPath.
            replaceFirst(srcMainScalaDir.getPath, docsrcDir.getPath).
            replaceFirst(managedSrcMainScalaDir.getPath, docsrcDir.getPath)

        if (srcPath == docsrcPath)
          throw new RuntimeException("unexpected source path ["+ srcPath +"]")

        copyDocFile(srcFile, file(docsrcPath))
      }

    val javaSources = srcFiles.filter(_.name.endsWith(".java")).toSet
    val javaTagFiles = JavaTagDocumenter.docJavaTags(javaSources)

    scalaFiles ++ javaTagFiles
  }

  //
  // Copies a file, doing a little filtering along the way to make
  // destination file suitable for use in generating scaladocs.
  //
  // Returns destination file.
  //
  private def copyDocFile(srcFile: File, destFile: File): File = {
    if (!destFile.exists || (destFile.lastModified < srcFile.lastModified)) {
      IO.createDirectory(file(destFile.getParent))

      val writer = new PrintWriter(destFile)

      try {
        for (line <- Source.fromFile(srcFile).getLines)
          writer.println(line.replaceFirst("@Finders(.*)", ""))
      }
      finally { writer.close }
    }
    destFile
  }

  //
  // Adds customization to scaladocs.
  //
  // Appends additional css to template.css file and copies
  // additional gifs into lib directory.
  //
  // Note: found that adding new gifs into lib directory causes
  // doc task to rebuild scaladocs from scratch each time.
  // Without that it only rebuilds if needed.
  //
  def docTask(docDir: File, srcDir: File, projectName: String): File = {
    val docLibDir = docDir / "lib"
    val htmlSrcDir = srcDir / "html"
    val cssFile = docLibDir / "template.css"
    val addlCssFile = htmlSrcDir / "addl.css"

    val css = Source.fromFile(cssFile).mkString
    val addlCss = Source.fromFile(addlCssFile).mkString

    if (!css.contains("pre.stHighlighted")) {
      val writer = new PrintWriter(cssFile)

      try {
        writer.println(css)
        writer.println(addlCss)
      }
      finally { writer.close }
    }

    if (projectName == "scalatest") {
      (htmlSrcDir * "*.gif").get.foreach { gif =>
        IO.copyFile(gif, docLibDir / gif.name)
      }
    }
    docDir
  }

  lazy val projectTitle =
    settingKey[String]("Name of project to display in doc titles")

  lazy val docsrcDir =
    settingKey[File](
      "Directory to hold processed source files for generating scaladocs")

  val docsrcDirSetting =
     docsrcDir := target.value / "docsrc"

  val docSourcesSetting =
     sources in (Compile, doc) :=
       genDocSources((sources in Compile).value,
                     (sourceDirectory in Compile).value,
                     (sourceManaged in Compile).value,
                     docsrcDir.value)

  val docScalacOptionsSetting =
    scalacOptions in (Compile, doc) ++= 
      Seq[String](
        "-sourcepath", docsrcDir.value.getAbsolutePath,
        "-doc-title", projectTitle.value +" "+ releaseVersion,
        "-doc-source-url", docSourceUrl)

  val scalatestDocTaskSetting =
    doc in Compile := docTask((doc in Compile).value,
                              (sourceDirectory in Compile).value,
                              name.value)

  val scalautilsDocTaskSetting =
    doc in Compile := docTask((doc in Compile).value,
                              (sourceManaged in Compile).value,
                              name.value)
}
