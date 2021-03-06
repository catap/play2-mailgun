name := "play2-mailgun"

val deps = Seq(
		"com.typesafe.play"       %%  "play-ws"               % "2.4.6"      % "provided",
    "org.mockito"                 %   "mockito-all"           % "1.10.19"     % "test",
    "org.specs2"                  %%  "specs2"                % "2.3.13"      % "test",
		"commons-fileupload" 					% 	"commons-fileupload" 		% "1.3.1"       % "test",
    "javax.servlet" 							% 	"javax.servlet-api" 		% "3.1.0"       % "test"
)

lazy val commonSettings = Seq(
	organization := "com.themillhousegroup",
	// If the CI supplies a "build.version" environment variable, inject it as the rev part of the version number:
	version := s"${sys.props.getOrElse("build.majorMinor", "0.3")}.${sys.props.getOrElse("build.version", "SNAPSHOT")}",
	scalaVersion := "2.11.7",
	crossScalaVersions := Seq("2.11.7", "2.10.5"),
	libraryDependencies ++= deps
)



lazy val root = project.in(file(".")).settings(commonSettings:_*)
lazy val templating = project.in(file("templating")).settings(commonSettings:_*)


resolvers ++= Seq(  "oss-snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
                    "oss-releases"  at "https://oss.sonatype.org/content/repositories/releases",
                    "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/")

jacoco.settings

publishArtifact in (Compile, packageDoc) := false

seq(bintraySettings:_*)

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

scalariformSettings

net.virtualvoid.sbt.graph.Plugin.graphSettings

