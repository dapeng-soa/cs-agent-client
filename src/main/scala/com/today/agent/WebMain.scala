package com.today.agent

import com.github.dapeng.socket.enums.EventType
import io.socket.client.{IO, Socket}
import io.socket.emitter.Emitter

object WebMain {

  def main(args: Array[String]): Unit = {

    val opts = new IO.Options()
    opts.forceNew = true

    val socketClient: Socket = IO.socket("http://127.0.0.1:9095", opts)

    socketClient.on(Socket.EVENT_CONNECT, new Emitter.Listener {
      override def call(args: AnyRef*): Unit = {
        println(s" connected......clientId: ${socketClient.id()}...")

        socketClient.emit(EventType.WEB_REG.name, "app1:127.0.0.1")
      }
    }).on("serviceList", new Emitter.Listener {
      override def call(args: AnyRef*): Unit = {
        println(s" received server event: serverList, args: ${args}")

        socketClient.emit(EventType.GET_SERVER_TIME.name, "hello")
      }
    })
  }
}
