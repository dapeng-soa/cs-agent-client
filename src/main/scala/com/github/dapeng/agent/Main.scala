package com.github.dapeng.agent

import java.io.{File, FileWriter}
import java.util.concurrent.{Executors, LinkedBlockingQueue}

import com.github.dapeng.socket.entity._
import com.github.dapeng.socket.enums.EventType
import com.github.dapeng.socket.util.IPUtils
import com.google.gson.Gson
import com.github.dapeng.agent.client.CmdExecutor
import com.github.dapeng.agent.listener.DeployServerOperations
import io.socket.client.{IO, Socket}
import io.socket.emitter.Emitter
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._
import scala.io.Source

object Main {

  val LOGGER: Logger = LoggerFactory.getLogger(this.getClass)
  val gson: Gson = new Gson

  def main(args: Array[String]): Unit = {

    var serverUrl = "http://127.0.0.1:6886"
    val registerInfo = s"${IPUtils.nodeName}:${IPUtils.localIp}"

    if (args != null && args.length >= 1) {
      serverUrl = args.head
    } else {
      LOGGER.info("connect serverUrl not set ,Please set e.g. [http://127.0.0.1:6886]")
      System.exit(1)
    }

    LOGGER.info(s"connect serverUrl:$serverUrl")
    LOGGER.info(s"registerInfo:$registerInfo")

    //TODO 测试
    //LOGGER.info(classOf[DeployServerShellInvoker].getClassLoader.getResource("./"))
    //val yamlFileDir = s"${classOf[DeployServerShellInvoker].getClassLoader.getResource("./").getPath()}yamlDir"

    //TODO jar包运行
    import java.io.UnsupportedEncodingException
    var jarWholePath = this.getClass.getProtectionDomain.getCodeSource.getLocation.getFile
    try
      jarWholePath = java.net.URLDecoder.decode(jarWholePath, "UTF-8")
    catch {
      case e: UnsupportedEncodingException =>
        LOGGER.info(s"get jar basePath error:$e")
    }
    val basePath = new File(jarWholePath).getParentFile.getAbsolutePath
    LOGGER.info(s"basePath:$basePath")
    val yamlFileDir = "yamlDir"

    val socketClient: Socket = IO.socket(serverUrl)

    val queue = new LinkedBlockingQueue[String]()
    val buildQueue = new LinkedBlockingQueue[String]()
    val cmdExecutor = new CmdExecutor(queue, socketClient)
    val deployExecutor = new CmdExecutor(buildQueue, socketClient)

    val threads = Executors.newFixedThreadPool(2)

    threads.execute(cmdExecutor)
    threads.execute(deployExecutor)

    new Thread(cmdExecutor).start()

    socketClient.on(Socket.EVENT_CONNECT, new Emitter.Listener {
      override def call(args: AnyRef*): Unit = {
        LOGGER.info(s" connected......clientId: ${socketClient.id()}...")


        socketClient.emit(EventType.NODE_REG.name, registerInfo)
      }
    }).on(EventType.BUILD.name, new Emitter.Listener {
      override def call(args: AnyRef*): Unit = {
        val buildJson = args(0).asInstanceOf[String]
        val buildVo = gson.fromJson(buildJson, classOf[BuildVo])
        buildVo.getBuildServices.asScala.foreach(vo => {
          // 依赖服务名 依赖服务git地址 依赖服务分支名 依赖服务构建脚本 依赖项目的基础镜像  真正需要构建(发布)的服务
          val cmd = s"${EventType.BUILD.name} ${vo.getServiceName} ${vo.getGitURL} ${vo.getBranchName} ${vo.getImageName} ${buildVo.getBuildService} ${buildVo.getDeployHost} ${buildVo.getId} ${vo.getBuildOperation}"
          LOGGER.info(s" start to execute cmd: ${cmd}")
          buildQueue.add(cmd)
        })
      }

    }).on(EventType.GET_SERVER_INFO.name, new Emitter.Listener() {
      override def call(args: AnyRef*) {
        val deployVoJson = args(0).asInstanceOf[String]
        val request = gson.fromJson(deployVoJson, classOf[DeployRequest])
        val cmd = s"${EventType.GET_SERVER_INFO_RESP.name} ${request.getServiceName} $basePath/$yamlFileDir/${request.getServiceName}.yml"
        queue.put(cmd)
      }
    }).on("webCmd", new DeployServerOperations(queue, socketClient)).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
      override def call(args: AnyRef*) {
        LOGGER.info(" disconnected ........")
      }
    }).on(EventType.DEPLOY.name, new Emitter.Listener {
      override def call(objects: AnyRef*): Unit = {
        val voString = objects(0).asInstanceOf[String]
        val vo = gson.fromJson(voString, classOf[DeployVo])
        // 优先生成服务挂载所需的配置文件
        if (null != vo.getVolumesFiles) {
          try {
            vo.getVolumesFiles.asScala.toList.foreach(x => {
              // 获取文件的存储目录
              val path = x.getFileName.substring(2, x.getFileName.lastIndexOf("/"))
              // 文件名
              val name = x.getFileName.substring(x.getFileName.lastIndexOf("/") + 1)
              val filePath = new File(s"$basePath/$yamlFileDir/$path")
              // 没有就创建目录
              if (!filePath.exists()) {
                if (filePath.mkdirs()) {
                  LOGGER.info(s"::mkdir [ $filePath ] success !")
                } else {
                  LOGGER.warn(s"::mkdir [ $filePath ] error !")
                }
              }
              val genFile = new File(filePath.getAbsolutePath, name)
              val writer = new FileWriter(genFile)
              try {
                // 文件内容直接写就是了
                val finalContent = Source.fromString(x.getFileContext).getLines()
                finalContent.foreach(i => {
                  writer.write(i)
                  writer.write("\n")
                })
                writer.flush()
                genFile.setLastModified(vo.getLastModifyTime)
              } catch {
                case e: Exception => {
                  LOGGER.error(s" failed to write file: ${x.getFileName}.......${e.getMessage}")
                  socketClient.emit(EventType.ERROR_EVENT.name, "failed to write file")
                }
              } finally {
                writer.close()
              }
            })
          } catch {
            case e: Exception => {
              LOGGER.error(s" failed to write file ${e.getMessage}")
              socketClient.emit(EventType.ERROR_EVENT.name, "failed to write file")
            }
          }
        }

        // 生成服务yaml文件
        val yamlDir = new File(new File(basePath), yamlFileDir)
        if (!yamlDir.exists()) {
          yamlDir.mkdirs()
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
          LOGGER.info(s"set service ${vo.getServiceName} LastModifyTime is:${vo.getLastModifyTime}")
          yamlFile.setLastModified(vo.getLastModifyTime)
          //yamlFile.setReadOnly()
        } catch {
          case e: Exception => {
            LOGGER.info(s" failed to write file: ${yamlDir.getAbsolutePath}/${vo.getServiceName}.yml.......${e.getMessage}")
            socketClient.emit(EventType.ERROR_EVENT.name, "update failed")
          }
        } finally {
          writer.close()
        }

        //exec cmd.....
        val cmd = s"${EventType.DEPLOY_RESP.name} ${vo.getServiceName}  ${yamlDir.getAbsolutePath}/${vo.getServiceName}.yml"
        queue.put(cmd)
      }
    }).on(EventType.GET_YAML_FILE.name, new Emitter.Listener {
      override def call(objects: AnyRef*): Unit = {
        val deployVoJson = objects(0).asInstanceOf[String]
        val deployRequest = gson.fromJson(deployVoJson, classOf[DeployRequest])
        val cmd = s"${EventType.GET_YAML_FILE.name} $basePath/$yamlFileDir/${deployRequest.getServiceName}.yml"
        queue.put(cmd)
      }

    }).on(EventType.STOP.name, new Emitter.Listener {
      override def call(objects: AnyRef*): Unit = {
        val deployVoJson = objects(0).asInstanceOf[String]
        val deployRequest = gson.fromJson(deployVoJson, classOf[DeployRequest])
        val cmd = s"${EventType.STOP_RESP.name} ${deployRequest.getServiceName}"
        queue.put(cmd)
      }
    }).on(EventType.RESTART.name, new Emitter.Listener {
      override def call(objects: AnyRef*): Unit = {
        val deployVoJson = objects(0).asInstanceOf[String]
        val deployRequest = gson.fromJson(deployVoJson, classOf[DeployRequest])
        val cmd = s"${EventType.RESTART_RESP.name} ${deployRequest.getServiceName}"
        queue.put(cmd)
      }
    }).on(EventType.WEB_LEAVE.name, new Emitter.Listener {
      override def call(args: AnyRef*): Unit = {
        LOGGER.info(":::web node leave")
      }
    }).on(EventType.SYNC_NETWORK.name, new Emitter.Listener {
      override def call(objects: AnyRef*): Unit = {
        val deployVoJson = objects(0).asInstanceOf[String]
        val vo = gson.fromJson(deployVoJson, classOf[SyncNetworkVo])
        val cmd = s"${EventType.SYNC_NETWORK_RESP.name} ${vo.getNetworkName} ${vo.getDriver} ${vo.getSubnet} ${vo.getOpt}"
        queue.put(cmd)
      }
    }).on(EventType.RM_CONTAINER.name, new Emitter.Listener {
      override def call(objects: AnyRef*): Unit = {
        val deployVoJson = objects(0).asInstanceOf[String]
        val deployRequest = gson.fromJson(deployVoJson, classOf[DeployRequest])
        val cmd = s"${EventType.RM_CONTAINER_RESP.name} ${deployRequest.getServiceName}"
        queue.put(cmd)
      }
    }).on(EventType.REMOTE_DEPLOY.name, new Emitter.Listener {
      override def call(objects: AnyRef*): Unit = {
        val data = objects(0).asInstanceOf[String]
        val info = data.split(":::")
        val buildId = info(1)
        val sourceIp = info(2)
        val deployHost = info(3)
        val serviceName = info(4)
        val imageName = info(5)
        val imageTag = info(6)
        val cmd = s"${EventType.REMOTE_DEPLOY_RESP.name} $buildId $sourceIp $deployHost $serviceName $imageName $imageTag"
        buildQueue.put(cmd)
      }
    })
    socketClient.connect()
  }
}
