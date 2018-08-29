import java.io.{FileInputStream, FileOutputStream}

name := "agent_bash"

version := "1.0"

scalaVersion := "2.12.4"

mainClass in assembly := Some("com.today.agent.Main")

/**
  */
libraryDependencies ++= Seq(
  "org.yaml" % "snakeyaml" % "1.17",
  "com.google.code.gson" % "gson" % "2.3.1",
  "io.socket" % "socket.io-client" % "0.8.1",
  "com.github.dapeng" %% "agent_server-api" % "1.0-SNAPSHOT",
  "com.github.wangzaixiang" %% "scala-sql" % "2.0.6",
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "ch.qos.logback" % "logback-core" % "1.1.3",
  "org.slf4j" % "slf4j-api" % "1.7.13",
  "org.slf4j" % "jcl-over-slf4j" % "1.7.25"
)

lazy val dist = taskKey[File]("make a dist scompose file")

dist := {
  val assemblyJar = assembly.value

  val distJar = new java.io.File(target.value, "agentClient")
  val out = new FileOutputStream(distJar)

  out.write(
    """#!/usr/bin/env sh
      |exec java -jar -XX:+UseG1GC "$0" "$@"
      |""".stripMargin.getBytes)

  val inStream = new FileInputStream(assemblyJar)
  val buffer = new Array[Byte](1024)

  while( inStream.available() > 0) {
    val length = inStream.read(buffer)
    out.write(buffer, 0, length)
  }

  out.close

  distJar.setExecutable(true, false)
  println("=================================")
  println(s"build agent at ${distJar.getAbsolutePath}" )

  distJar
}