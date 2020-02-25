package com.github.dapeng.agent.cmd

import com.github.dapeng.socket.enums.EventType
import com.google.gson.Gson
import org.slf4j.LoggerFactory

/**
  * @author with struy.
  *         Create by 2019-01-13 20:48
  *         email :yq1724555319@gmail.com
  */

case class CmdOutPutThread(socket: java.net.Socket, ioSocket: io.socket.client.Socket, sourceClient: String) extends Thread {

  private val gson = new Gson()
  private val LOGGER = LoggerFactory.getLogger(CmdOutPutThread.getClass)

  override def run(): Unit = {
    LOGGER.info(s"client::: $sourceClient Responder thread runing")
    try {
      var bytes = new Array[Byte](1024)
      while (!this.isInterrupted && !socket.isClosed) {
        val n = socket.getInputStream.read(bytes)
        // exit指令后没有可读,发送exited事件
        if (n == -1) {
          ioSocket.emit(EventType.CMD_EXITED.name, sourceClient)
          this.interrupt()
        }
        val res = new String(bytes, 0, n)
        val outputVo = new CmdOutputVo()
        outputVo.setSourceClient(sourceClient)
        outputVo.setOutput(res)
        ioSocket.emit(EventType.CMD_RESP.name, gson.toJson(outputVo))
        bytes = new Array[Byte](1024)
      }
    } catch {
      case e: Exception => LOGGER.error(s"socket read inputStream error $e")
    }
  }
}
