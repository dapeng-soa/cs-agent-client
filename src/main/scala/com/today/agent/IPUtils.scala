package com.today.agent

import java.net._

/**
  * @author with struy.
  *         Create by 2018/8/6 16:11
  *         email :yq1724555319@gmail.com
  */

object IPUtils {
  private var inetAddress = InetAddress.getLocalHost
  try {
    if (inetAddress.getHostAddress == null || "127.0.0.1".equals(inetAddress.getHostAddress)) {
      val ni: NetworkInterface = NetworkInterface.getByName("bond0")
      if (ni == null)
        throw new RuntimeException("wrong with get ip")

      val ips = ni.getInetAddresses
      while (ips.hasMoreElements) {
        val nextElement: InetAddress = ips.nextElement()
        if ("127.0.0.1".equals(nextElement.getHostAddress()) || nextElement.isInstanceOf[Inet6Address] || nextElement.getHostAddress.contains(":"))
          inetAddress = nextElement
      }
    }
  } catch {
    // fixme
    case e: UnknownHostException =>
      e.printStackTrace()
    case e1: SocketException =>
      e1.printStackTrace()
  }

  /**
    * local hostIp
    *
    * @return
    */
  def localIp: String = {
    try
      InetAddress.getLocalHost.getHostAddress
    catch {
      case e: UnknownHostException =>
        e.printStackTrace()
        "unknown"
    }
  }

  /**
    * local hostname
    *
    * @return
    */
  def localHostName: String = {
    try
      InetAddress.getLocalHost.getHostName
    catch {
      case e: UnknownHostException =>
        e.printStackTrace()
        "unknown"
    }
  }

  /**
    * 节点名称
    * @return
    */
  def nodeName: String = {
    val nodeName = System.getenv("nodeName")
    if (nodeName == null) {
      localHostName
    } else {
      nodeName
    }
  }

  def main(args: Array[String]): Unit = {
    println(nodeName)
  }

}
