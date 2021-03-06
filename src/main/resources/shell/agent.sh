#!/bin/bash
getServerInfoResp(){
    if [ $# -lt 2 ];then
        echo "invalid cmd...please input your request [serviceName],[serviceName.yml]"
        exit 1
    fi
   ip=$(ifconfig eth0|grep "inet "|awk '{print $2}')


   if [ -e "$2" ];then
        time=`stat -c %Y $2`
   else
        time=0
   fi

    result1=`docker ps | grep -w "$1$" | awk '{print $2}' | awk -F ':' '{print $NF}'`

    if [[ -z $result1 ]]; then
        result="$ip:$1:false:$time:none"
    else
        result="$ip:$1:true:$time:$result1"
    fi

echo $result
}

deployResp() {
  if [ $# -lt 2 ];then
        echo "invalid cmd...please input your request [serviceName],[serviceName.yml]"
        exit 1
    fi
  serviceName="$1"
  ymlFile="$2"

  if [ ! -f "$ymlFile" ]; then
    echo "找不到对应的$ymlFile"
  else
    docker-compose -p $serviceName -f $ymlFile up -d
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

case $1 in
   "getServerInfoResp" | "deployResp" | "stopResp" | "restartResp" | "getYamlFile" |"getYamlFileResp") eval $@ ;;
   *) echo "invalid command $1" ;;
esac
