package com.today.agent

import java.io.{File, FileWriter}
import java.util.concurrent.LinkedBlockingQueue

import com.github.dapeng.socket.entity.{DeployRequest, DeployVo}
import com.github.dapeng.socket.enums.EventType
import com.github.dapeng.socket.util.IPUtils
import com.google.gson.Gson
import com.today.agent.client.{CmdExecutor, DeployServerShellInvoker}
import com.today.agent.listener.DeployServerOperations
import io.socket.client.{IO, Socket}
import io.socket.emitter.Emitter

import scala.io.Source

object Main {

  def main(args: Array[String]): Unit = {

    var serverUrl = "http://127.0.0.1:9095"
    val registerInfo = s"${IPUtils.nodeName}:${IPUtils.localIp}"

    if (args != null && args.length >= 1) {
      serverUrl = args.head
    }

    println(s"connect serverUrl:$serverUrl")
    println(s"registerInfo:$registerInfo")

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
    }).on(EventType.GET_SERVER_TIME.name,  new Emitter.Listener() {
      override def call( args: AnyRef*) {
        val serviceName = args(0)
        val cmd = s"${EventType.GET_SERVER_TIME.name} ${classOf[DeployServerShellInvoker].getClassLoader.getResource("./").getPath()}yamlDir/$serviceName.yml"
        queue.put(cmd)
      }
    }).on("webCmd", new DeployServerOperations(queue,socketClient)).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
      override def call( args: AnyRef*) {
        println(" disconnected ........")
      }
    }).on(EventType.DEPLOY.name, new Emitter.Listener {
      override def call(objects: AnyRef*): Unit = {
        val voString = objects(0).asInstanceOf[String]
        val vo = new Gson().fromJson(voString, classOf[DeployVo])
        val yamlDir = new File(new File(classOf[DeployServerShellInvoker].getClassLoader.getResource("./").getPath()), "yamlDir")
        if (!yamlDir.exists()) {
          yamlDir.mkdir();
        }

        val yamlFile = new File(yamlDir.getAbsolutePath, s"${vo.getServiceName}.yml")
        yamlFile.setLastModified(vo.getLastModifyTime)
        val writer = new FileWriter(yamlFile)
        try {
          val finalContent = Source.fromString(vo.getFileContent).getLines().filterNot(_.startsWith("!!")).filterNot(_.contains("null"))
          finalContent.foreach(i => {
            writer.write(i)
            writer.write("\n")
          })

          writer.flush();
        } catch {
          case e: Exception => println(s" failed to write file.......${e.getMessage}")
        } finally {
          writer.close()
        }

        //exec cmd.....
        val cmd = s"${EventType.DEPLOY.name.toLowerCase()} ${yamlDir.getAbsolutePath}/${vo.getServiceName}"
        queue.put(cmd)
      }
    }).on(EventType.GET_YAML_FILE.name, new Emitter.Listener{
      override def call(objects: AnyRef*): Unit = {
        val deployVoJson = objects(0).asInstanceOf[String]
        val deployRequest = new Gson().fromJson(deployVoJson, classOf[DeployRequest])
        val cmd = s"${EventType.GET_YAML_FILE.name} ${classOf[DeployServerShellInvoker].getClassLoader.getResource("./").getPath()}yamlDir/${deployRequest.getServiceName}.yml"
        queue.put(cmd)
      }

    }).on(EventType.STOP.name, new Emitter.Listener{
      override def call(objects: AnyRef*): Unit = {
        val deployVoJson = objects(0).asInstanceOf[String]
        val deployRequest = new Gson().fromJson(deployVoJson, classOf[DeployRequest])
        val cmd = s"stop ${deployRequest.getServiceName}"
        queue.put(cmd)
      }
    }).on(EventType.RESTART.name, new Emitter.Listener{
      override def call(objects: AnyRef*): Unit = {
        val deployVoJson = objects(0).asInstanceOf[String]
        val deployRequest = new Gson().fromJson(deployVoJson, classOf[DeployRequest])
        val cmd = s"restart ${deployRequest.getServiceName}"
        queue.put(cmd)
      }
    })

    socketClient.connect()
  }
}
