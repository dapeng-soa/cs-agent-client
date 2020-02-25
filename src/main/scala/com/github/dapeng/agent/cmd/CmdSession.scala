package com.github.dapeng.agent.cmd

import java.net.Socket

import org.apache.commons.logging.LogFactory

/**
  * @author with struy.
  *         Create by 2019-01-13 19:20
  *         email :yq1724555319@gmail.com
  */

case class CmdSession
(
  sourceClient: String,
  containerId: String,
  execId: String,
  socket: Socket,
  cmdOutPutThread: CmdOutPutThread
) {
  private val LOGGER = LogFactory.getLog(CmdSession.getClass)

  def destroy(): Unit = {
    try {
      cmdOutPutThread.interrupt()
    } catch {
      case e: Exception => {
        // LOGGER
        LOGGER.info("cmd output isInterrupted , close socket")
        socket.close()
      }
    }
  }
}
