package com.today.agent

import java.io.{File, FileWriter}
import java.util.concurrent.LinkedBlockingQueue

import com.github.dapeng.socket.entity.{DeployRequest, DeployVo}
import com.github.dapeng.socket.enums.EventType
import com.github.dapeng.socket.util.IPUtils
import com.google.gson.Gson
import com.today.agent.client.CmdExecutor
import com.today.agent.listener.DeployServerOperations
import io.socket.client.{IO, Socket}
import io.socket.emitter.Emitter

import scala.io.Source

object Main {

  def main(args: Array[String]): Unit = {

    var serverUrl = "" //http://127.0.0.1:6886
    val registerInfo = s"${IPUtils.nodeName}:${IPUtils.localIp}"

    if (args != null && args.length >= 1) {
      serverUrl = args.head
    } else {
      println("connect serverUrl not set ,Please set e.g. [http://127.0.0.1:6886]")
      System.exit(1)
    }

    println(s"connect serverUrl:$serverUrl")
    println(s"registerInfo:$registerInfo")

    //TODO 测试
    //println(classOf[DeployServerShellInvoker].getClassLoader.getResource("./"))
    //val yamlFileDir = s"${classOf[DeployServerShellInvoker].getClassLoader.getResource("./").getPath()}yamlDir"

    //TODO jar包运行
    import java.io.UnsupportedEncodingException
    var jarWholePath = this.getClass.getProtectionDomain.getCodeSource.getLocation.getFile
    try
      jarWholePath = java.net.URLDecoder.decode(jarWholePath, "UTF-8")
    catch {
      case e: UnsupportedEncodingException =>
        println(s"get jar basePath error:$e")
    }
    val basePath = new File(jarWholePath).getParentFile.getAbsolutePath
    println(s"basePath:$basePath")
    val yamlFileDir = "yamlDir"

    val opts = new IO.Options()
    opts.forceNew = true

    val socketClient: Socket = IO.socket(serverUrl, opts)

    val queue = new LinkedBlockingQueue[String]()
    val cmdExecutor = new CmdExecutor(queue, socketClient)

    new Thread(cmdExecutor).start()

    socketClient.on(Socket.EVENT_CONNECT, new Emitter.Listener {
      override def call(args: AnyRef*): Unit = {
        println(s" connected......clientId: ${socketClient.id()}...")


        socketClient.emit(EventType.NODE_REG.name, registerInfo)
      }
    }).on(EventType.GET_SERVER_TIME.name, new Emitter.Listener() {
      override def call(args: AnyRef*) {
        val serviceName = args(0)
        val cmd = s"${EventType.GET_SERVER_TIME_RESP.name} $basePath/$yamlFileDir/$serviceName.yml"
        queue.put(cmd)
      }
    }).on("webCmd", new DeployServerOperations(queue, socketClient)).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
      override def call(args: AnyRef*) {
        println(" disconnected ........")
      }
    }).on(EventType.DEPLOY.name, new Emitter.Listener {
      override def call(objects: AnyRef*): Unit = {
        val voString = objects(0).asInstanceOf[String]
        val vo = new Gson().fromJson(voString, classOf[DeployVo])
        val yamlDir = new File(new File(basePath), yamlFileDir)
        if (!yamlDir.exists()) {
          yamlDir.mkdir()
        }

        val yamlFile = new File(yamlDir.getAbsolutePath, s"${vo.getServiceName}.yml")
        val writer = new FileWriter(yamlFile)
        try {
          val finalContent = Source.fromString(vo.getFileContent).getLines().filterNot(_.startsWith("!!")).filterNot(_.contains("null"))
          finalContent.foreach(i => {
            writer.write(i)
            writer.write("\n")
          })
          writer.flush()
          println(s"set service ${vo.getServiceName} LastModifyTime is:${vo.getLastModifyTime}")
          yamlFile.setLastModified(vo.getLastModifyTime)
          //yamlFile.setReadOnly()
        } catch {
          case e: Exception => println(s" failed to write file.......${e.getMessage}")
        } finally {
          writer.close()
        }

        //exec cmd.....
        val cmd = s"${EventType.DEPLOY_RESP.name.toLowerCase()} ${yamlDir.getAbsolutePath}/${vo.getServiceName}"
        queue.put(cmd)
      }
    }).on(EventType.GET_YAML_FILE.name, new Emitter.Listener {
      override def call(objects: AnyRef*): Unit = {
        val deployVoJson = objects(0).asInstanceOf[String]
        val deployRequest = new Gson().fromJson(deployVoJson, classOf[DeployRequest])
        val cmd = s"${EventType.GET_YAML_FILE.name} $basePath/$yamlFileDir/${deployRequest.getServiceName}.yml"
        queue.put(cmd)
      }

    }).on(EventType.STOP.name, new Emitter.Listener {
      override def call(objects: AnyRef*): Unit = {
        val deployVoJson = objects(0).asInstanceOf[String]
        val deployRequest = new Gson().fromJson(deployVoJson, classOf[DeployRequest])
        val cmd = s"${EventType.STOP_RESP.name} ${deployRequest.getServiceName}"
        queue.put(cmd)
      }
    }).on(EventType.RESTART.name, new Emitter.Listener {
      override def call(objects: AnyRef*): Unit = {
        val deployVoJson = objects(0).asInstanceOf[String]
        val deployRequest = new Gson().fromJson(deployVoJson, classOf[DeployRequest])
        val cmd = s"${EventType.RESTART_RESP.name} ${deployRequest.getServiceName}"
        queue.put(cmd)
      }
    }).on(EventType.GET_SERVICE_STATUS.name, new Emitter.Listener {
      override def call(args: AnyRef*): Unit = {
        while (true ) {
          val serviceName = args(0).asInstanceOf[String]
          val cmd = s"${EventType.GET_SERVICE_STATUS_RESP} ${serviceName}"
          queue.put(cmd)
          Thread.sleep(10000)
        }
      }
    })

    socketClient.connect()
  }
}
