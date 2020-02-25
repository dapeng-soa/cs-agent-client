package com.github.dapeng.agent

import java.io.{File, FileWriter, IOException}
import java.util
import java.util.concurrent.{Executors, LinkedBlockingQueue}

import com.github.dapeng.agent.client.CmdExecutor
import com.github.dapeng.agent.cmd.{CmdOutPutThread, CmdSession}
import com.github.dapeng.agent.listener.DeployServerOperations
import com.github.dapeng.socket.entity._
import com.github.dapeng.socket.enums.EventType
import com.github.dapeng.socket.util.IPUtils
import com.google.gson.Gson
import com.spotify.docker.client.exceptions.{ContainerNotFoundException, DockerRequestException}
import com.spotify.docker.client.{DefaultDockerClient, DockerClient}
import com.spotify.docker.client.messages.{ContainerConfig, ExecCreation, HostConfig, PortBinding}
import io.socket.client.{IO, Socket}
import io.socket.emitter.Emitter
import org.json.JSONObject
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.io.Source

object Main {

  val LOGGER: Logger = LoggerFactory.getLogger(this.getClass)
  val gson: Gson = new Gson
  var docker: DefaultDockerClient = DefaultDockerClient.fromEnv().build()
  val DOCKER_PROXY_BIND_PORT = "2375"
  val DOCKER_PROXY_SOCAT_IMAGE = "alpine/socat:1.0.3"
  val DOCKER_PROXY_CONTAINER_EXPOSED_PORT = "2375"
  val DOCKER_PROXY_VOLFILE = "/var/run/docker.sock"
  val DOCKER_PROXY_CONTAINER_NAME = "socat-docker-proxy"
  val execSessionMap = new mutable.HashMap[String, CmdSession]()

  def main(args: Array[String]): Unit = {

    var serverUrl = "http://10.0.75.1:6886" //http://127.0.0.1:6886
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

    try {
      caeateDockerPorxy()
    } catch {
      case e: Exception => {
        LOGGER.info(s"start docker proxy error: $e")
        System.exit(1)
      }
    }

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
        LOGGER.info(s":::web node leave::clientId $args")
        val webClientId = args(0).toString
        // 关闭连接，释放资源
        closeCmdSession(webClientId)
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
    }).on(EventType.CMD_EVENT.name, new Emitter.Listener {
      override def call(objects: AnyRef*): Unit = {
        val request: CmdRequest = gson.fromJson(objects(0).toString, classOf[CmdRequest])
        try {
          // 如果会话不存在则创建
          if (!execSessionMap.contains(request.getSourceClientId)) {

            // 查看docker是不是关闭了
            checkDockerAttach()
            // create
            val execId = createBash(request.getContainerId)
            // 连接
            val socket: java.net.Socket = connectExec(execId)
            // 终端大小
            resizeTty(request.getWidth, request.getHeight, execId)
            // 就绪后才监听输出
            val thread = CmdOutPutThread(socket, socketClient, request.getSourceClientId)
            thread.setName(s"CmdOutPutThread-[${socketClient.id}]")
            thread.start()
            // 缓存
            execSessionMap.put(request.getSourceClientId, CmdSession(request.getSourceClientId, request.getContainerId, execId, socket, thread))
            LOGGER.info(s"$execSessionMap")
          }
          // 输入bash指令
          execCmdOnSession(execSessionMap(request.getSourceClientId), request.getData)
        } catch {
          case e: Exception => {
            LOGGER.error(s"cmd exec failed $e")
            val errorOutput = new CmdOutputVo()
            errorOutput.setSourceClient(request.getSourceClientId)
            errorOutput.setOutput(s"${e.getMessage}\n\r")
            socketClient.emit(EventType.CMD_RESP.name, gson.toJson(errorOutput))
          }
        }
      }
    }
    )
    socketClient.connect()

    Runtime.getRuntime().addShutdownHook(new Thread(() => {
      try {
        docker.close()
        LOGGER.info("The JVM Hook is execute")
      } catch {
        case e: Exception => LOGGER.info(s"error ${e.getMessage}")
      }
    }))
  }


  /**
    * 创建bash
    *
    * @param containerId 容器id/名称
    * @return
    */
  def createBash(containerId: String): String = {
    val execCreation: ExecCreation = docker
      .execCreate(containerId, Array[String]("sh"), DockerClient.ExecCreateParam.attachStdin, DockerClient.ExecCreateParam.attachStdout, DockerClient.ExecCreateParam.attachStderr, DockerClient.ExecCreateParam.tty(true))
    execCreation.id()
  }

  /**
    * 连接bash
    *
    * @param execId
    * @throws
    * @return
    */
  @throws[IOException]
  private def connectExec(execId: String): java.net.Socket = {
    val socket = new java.net.Socket(docker.getHost, DOCKER_PROXY_BIND_PORT.toInt)
    socket.setKeepAlive(true)
    val out = socket.getOutputStream
    val obj = new JSONObject
    obj.put("Detach", false)
    obj.put("Tty", true)
    val json = obj.toString
    val pw =
      s"""POST /exec/$execId/start HTTP/1.1
         |Host: ${IPUtils.localIp}:$DOCKER_PROXY_BIND_PORT
         |User-Agent: Docker-Client
         |Content-Type: application/json
         |Connection: Upgrade
         |Content-Length: ${json.length}
         |Upgrade: tcp
         |
         |$json""".stripMargin
    out.write(pw.toString.getBytes("UTF-8"))
    out.flush()
    socket
  }

  /**
    * 检查docker状态
    */
  private def checkDockerAttach(): Unit = {
    try {
      docker.ping().equals("ok")
      LOGGER.info("docker is conn")
    } catch {
      case e: IllegalStateException => {
        LOGGER.info("docker closed, try conn")
        docker = DefaultDockerClient.fromEnv().build()
      }
    }
  }


  /**
    * 回收资源，当会话断开
    *
    * @param webClientId
    */
  private def closeCmdSession(webClientId: String): Unit = {
    if (execSessionMap.contains(webClientId)) {
      // 1.先发送【exit\r 】退出指令以退出bash
      exitSessionBash(execSessionMap(webClientId))
      // 2.销毁CmdSession
      execSessionMap(webClientId).destroy()
      // 3.移除CmdSession
      execSessionMap.remove(webClientId)
      // 4.如果Session是空的则直接关闭docker连接释放资源
      if (execSessionMap.isEmpty) {
        docker.close()
      }
    }
  }

  /**
    * 退出当前Session对应的bash
    *
    * @param session cmd会话
    */
  private def exitSessionBash(session: CmdSession): Unit = {
    execCmdOnSession(session, "\u0003", ":q!\r", "exit\r")
  }

  /**
    * 执行指令
    *
    * @param session 当前会话
    * @param cmds    指令内容
    */
  private def execCmdOnSession(session: CmdSession, cmds: String*): Unit = {
    cmds.foreach(cmd => {
      val out = session.socket.getOutputStream
      out.write(cmd.getBytes)
      out.flush()
    })
  }

  /**
    * 重置终端大小
    *
    * @param width
    * @param height
    * @param execId
    */
  private def resizeTty(width: String, height: String, execId: String): Unit = {
    docker.execResizeTty(execId, height.toInt, width.toInt)
  }

  /**
    * 启动docker代理程序
    */
  private def caeateDockerPorxy(): Unit = {
    try {
      docker.startContainer(DOCKER_PROXY_CONTAINER_NAME)
    } catch {
      case e1: DockerRequestException => {
        e1.status() match {
          case 304 => LOGGER.info(":::docker-proxy is runing")
          case _ => throw e1
        }
      }
      case e: ContainerNotFoundException => {
        LOGGER.info(":::start docker-proxy fail,try creat then!")
        // pull images
        docker.pull(DOCKER_PROXY_SOCAT_IMAGE)
        val portBindings = new util.HashMap[String, util.List[PortBinding]]
        val hostPorts = new util.ArrayList[PortBinding]
        hostPorts.add(PortBinding.of("127.0.0.1", DOCKER_PROXY_BIND_PORT))
        portBindings.put(DOCKER_PROXY_CONTAINER_EXPOSED_PORT + "/tcp", hostPorts)
        // exposed ports && volumes bind && restart always
        val hostConfig = HostConfig.builder
          .portBindings(portBindings)
          .appendBinds(HostConfig.Bind
            .from(DOCKER_PROXY_VOLFILE)
            .to(DOCKER_PROXY_VOLFILE)
            .readOnly(false)
            .build)
          .restartPolicy(HostConfig.RestartPolicy.always).build

        // Create container with exposed ports
        val containerConfig = ContainerConfig.builder
          .hostConfig(hostConfig)
          .image(DOCKER_PROXY_SOCAT_IMAGE)
          .exposedPorts(DOCKER_PROXY_CONTAINER_EXPOSED_PORT + "/tcp")
          .cmd("tcp-listen:" + DOCKER_PROXY_CONTAINER_EXPOSED_PORT + ",fork,reuseaddr", "unix-connect:" + DOCKER_PROXY_VOLFILE)
          .build
        val creation = docker.createContainer(containerConfig, DOCKER_PROXY_CONTAINER_NAME)
        val id = creation.id
        // start
        docker.startContainer(id)
      }
    }
  }
}
