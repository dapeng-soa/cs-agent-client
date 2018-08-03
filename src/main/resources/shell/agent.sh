#!/bin/bash

getServerTime(){
   #echo "received serviceName: $1"
   ip=$(ifconfig en0|grep "inet "|awk '{print $2}')
    if [ -f "$1" ];then
        echo "$ip:"`stat -c %Y $1`
    else
        echo "$ip:"0
    fi
}

deploy() {
  #should be absolute path
  ymlFile="$1.yml"

  if [ -f "$ymlFile" ]; then
    echo "找不到对应的$ymlFile"
  else
    `docker-compose -f $ymlFile up -d`
  fi
}

stop() {
  ip=$(ifconfig en0|grep "inet "|awk '{print $2}')
  echo " $ip stopping $1"
 `docker stop $1`
}

restart() {
   ip=$(ifconfig en0|grep "inet "|awk '{print $2}')
   echo " $ip restarting $1"
  `docker restart $1`
}

case $1 in
   "getServerTime" | "deploy") eval $@ ;;
   *) echo "invalid command $1" ;;
esac
