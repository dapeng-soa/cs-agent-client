#!/bin/bash

getServerTimeResp(){
   #echo "received serviceName: $1"
   ip=$(ifconfig eth0|grep "inet "|awk '{print $2}')
    if [ -f "$1" ];then
        echo "$ip:"`stat -c %Y $1`
    else
        echo "$ip:"0
    fi
}

deployResp() {
  #should be absolute path
  ymlFile="$1.yml"

  if [ ! -f "$ymlFile" ]; then
    echo "找不到对应的$ymlFile"
  else
    docker-compose -f $ymlFile up -d
  fi
}

stopResp() {
  ip=$(ifconfig eth0|grep "inet "|awk '{print $2}')
  echo $@
  echo " $ip stopping $1"
  docker stop $1
}

restartResp() {
   ip=$(ifconfig eth0|grep "inet "|awk '{print $2}')
   echo " $ip restarting $1"
  docker restart $1
}

getYamlFileResp() {
   cat $1
}
getYamlFile() {
   cat $1
}

getServiceStatusResp() {
    if [ $# -le 0 ];then
        echo "invalid cmd...please input your request serviceName"
        exit 1
    fi
    ip=$(ifconfig eth0|grep "inet "|awk '{print $2}')

    result=`docker ps | grep $1 | awk '{print $11}'`

    if [ -z $result ]; then
        echo "$ip:$1:false"
    else
        echo "$ip:$1:true"
    fi
}

case $1 in
   "getServerTimeResp" | "deployResp" | "stopResp" | "restartResp" | "getYamlFile" |"getYamlFileResp" |"getServiceStatusResp") eval $@ ;;
   *) echo "invalid command $1" ;;
esac
