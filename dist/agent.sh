#!/bin/bash
# run in Unix

ip=$(ifconfig eth0 2>/dev/null|grep "inet "|awk '{print $2}')
if [ ! $ip ];then
 ip=$(ifconfig eno1 2>/dev/null|grep "inet "|awk '{print $2}')
fi
if [ ! $ip ];then
 ip=$(ifconfig en0 2>/dev/null|grep "inet "|awk '{print $2}')
fi

getServerInfoResp(){
    if [ $# -lt 2 ];then
        echo "invalid cmd...please input your request [serviceName],[serviceName.yml]"
        exit 1
    fi

   if [ -e "$2" ];then
        time=`stat -c %Y $2 2>/dev/null`
        # macos
        if [ ! $time ];then
         time=`stat -f "%B %N" $2 | awk '{print$1}'`
        fi
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
    return 1
  else
    res=$(docker-compose -p $serviceName -f $ymlFile up -d 2>&1)
    if [ $? -ne 0 ]; then
        echo "$res"
        echo -e "\033[31m update $serviceName failed \033[0m"
        echo -e "\033[33m done \033[0m"
        return 1
    else
        echo "$res"
        echo -e "\033[32m update successful!!! \033[0m"
        echo -e "\033[33m done \033[0m"
        return 0
    fi
  fi
}

stopResp() {
  echo $@
   echo -e "\033[33m $ip stopping $1 \033[0m"
  docker stop $1
  if [ $? -ne 0 ]; then
    echo -e  "\033[31m stop $1 fail \033[0m"
    return 1
  else
    echo -e "\033[32m stop $1 success \033[0m"
    return 0
  fi
}

restartResp() {
    echo -e "\033[33m $ip restarting $1 \033[0m"
  docker restart $1
  if [ $? -ne 0 ]; then
    echo -e  "\033[31m restart $1 fail \033[0m"
    return 1
  else
    echo -e "\033[32m restart $1 success \033[0m"
    return 0
  fi
}

rmContainerResp() {
  echo $@
   echo -e "\033[33m $ip rm Container $1 \033[0m"
  docker rm $1
  if [ $? -ne 0 ]; then
    echo -e  "\033[31m rm Container $1 fail \033[0m"
    return 1
  else
    echo -e "\033[32m rm Container $1 success \033[0m"
    return 0
  fi
}

getYamlFileResp() {
   cat $1
}
getYamlFile() {
   cat $1
}

syncNetworkResp() {
  networkName="$1"
  driver="$2"
  subnet="$3"
  opt="$4"
  docker network create -d=$driver --subnet=$subnet -o=$opt $networkName
  if [ $? -ne 0 ]; then
    echo -e  "\033[31m $ip create network $networkName fail \033[0m"
    return 1
  else
    echo -e "\033[32m $ip create network $networkName success \033[0m"
    return 0
  fi
}

build() {
	# build info start
	serviceName=$1
	projectUrl=$2
	serviceBranch=$3
	imageName=$4
	realService=$5
	deployHost=$6
	buildId=$7
	cmd=`echo ${@:8}`
	echo -e "\033[33mbuild service [$serviceName] [$serviceBranch] start... \033[0m"
	echo -e "\033[32mbuild info=======================================start \033[0m"
	echo "|"
	echo "| buildId: [$buildId]"
	echo "| build realService:[$realService]"
	echo "| clientIp: [$ip]"
	echo "| deployHost: [$deployHost]"
	echo "| ori cmd: [$@]"
	echo "| serviceName: [$serviceName]"
	echo "| imageName: [$imageName]"
	echo "| projectUrl: [$projectUrl]"
	echo "| serviceBranch: [$serviceBranch]"
	echo "| cmd: [$cmd]"
	projectRootName=`echo ${2##*/} | cut -d . -f 1`
	echo "| projectGitName: [$projectRootName]|"
	WORKSPACE=`echo $COMPOSE_WORKSPACE`
	AGENT_PWD=`echo $AGENT_PATH`
	echo "| env WORKSPACE : [$WORKSPACE]|"
	echo "| env AGENT_HOME : [$AGENT_PWD]"
	echo "|"
	echo -e "\033[32mbuild info=======================================end \033[0m"
	# build info end
	# check start

	if [ ! -d "$WORKSPACE" ];
	then
		echo -e  "\033[31m 目录不存在，请添加COMPOSE_WORKSPACE环境变量指定代码空间: $WORKSPACE, 退出 \033[0m"
		echo $serviceName" BUILD_END:1"
		return 1
	fi

	if [ ! -d "$AGENT_PWD" ];
	then
		echo -e  "\033[31m 目录不存在,请添加AGENT_PATH环境变量指定agent目录: $AGENT_PWD, 退出 \033[0m"
		echo $serviceName" BUILD_END:1"
		return 1
	fi

	cd $WORKSPACE
	if [ ! -d $projectRootName ];
	then
		echo "项目不存在, 拉取项目: $projectUrl"
		git clone $projectUrl
		if [ $? -ne 0 ]; then
			echo -e "\033[31mclone faild \033[0m"
			echo $serviceName" BUILD_END:1"
			return 1
		fi
	else
		echo "项目已存在, 执行构建指令"
	fi

	# check end
	cd $AGENT_PWD
	if [ ! -f ".build.cache.ini" ];
	then
		echo ".build.cache.ini 文件不存在，新建"
		touch .build.cache.ini
	else
		echo ".build.cache.ini 文件已存在"
		cat .build.cache.ini
	fi

	oldGitId=`cat .build.cache.ini | grep $serviceName | awk -F "=" '{print $2}'`
	cd $WORKSPACE/$projectRootName
	echo -e "\033[32mupdate [$serviceName] code:::branch [$serviceBranch]================================================start \033[0m"
	git pull
	git checkout $serviceBranch
	git pull
	newGitId=`git rev-parse --short=7 HEAD`
	echo -e "\033[32mupdate [$serviceName] code:::branch [$serviceBranch]:::[$newGitId]====================================end \033[0m"
	echo 'oldGitId: '$oldGitId', newGitId: '$newGitId
	if [ "$newGitId" = "$oldGitId" ];
	then
		echo "gitId 一致，不需要重新构建，跳过..."
		BUILD_STATUS=$?
	else
	        #remove service old gitid
	        #echo "更新.build.cache.ini gitid"
	        #cd $AGENT_HOME
	        #sed -i "/^$1/d" .build.cache.ini
	        #add service new gitid at last line of .build.cache.ini
	        #echo "$1=$newGitId" >> .build.cache.ini

	        echo "执行指令: $cmd"
	        $cmd
	        BUILD_STATUS=$?
	    fi
	    if [ $BUILD_STATUS = 0 ];
	    then

	    #remove service old gitid
	    echo -e "\033[32m构建成功，更新gitid \033[0m"
	    cd $AGENT_PWD
	    sed -i "/^$serviceName/d" .build.cache.ini
	    #add service new gitid at last line of .build.cache.ini
	    echo "$serviceName=$newGitId" >> .build.cache.ini

	    ## if is realService ,deploy service
	    if [ "$serviceName" = "$realService" ]; then

	      echo -e "\033[32mbuild is realService , deploy realService \033[0m"
	      ## 如果 deployHost 与clientIp 不同，则需要将此次发布任务提交委托给其他机器
	      # 如果是远程服务器部署，需要推送镜像,不推送latest镜像,直接推送新的tag镜像，由远程自行打tag为latest
	      echo -e "\033[32mdocker push $imageName:$newGitId start\033[0m"
	      docker push $imageName:$newGitId
	      # 如何将服务部署指令发送到其他主机？
	      # 1.已标准输出返回给客户端，客户端根据关键字发送事件
	      # 标示关键字:源ip:部署节点:部署的服务:镜像名:最新的tag号
	      echo "[REMOTE_DEPLOY]:::$buildId:::$ip:::$deployHost:::$serviceName:::$imageName:::$newGitId"
	      echo "waiting deploy"
	      # 如果是远程部署，先把当前脚本停止，使其BUILD_END状态不被改变
	      return 1
		else
		  echo -e "\033[33m[$serviceName] not is realService , skip deploy\033[0m"
	    fi
	else
		echo "构建失败， 跳过更新gitid"
	fi
	echo $1" BUILD_END:$BUILD_STATUS"
}

remoteDeployResp(){
  buildId=$1
  sourceIp=$2
  deployHost=$3
  serviceName=$4
  imageName=$5
  imageTag=$6
  AGENT_PWD=`echo $AGENT_PATH`
  # 标示来源的节点地址
  sourceHostPre=":::[SOURCE_HOST]:::$sourceIp"

  echo -e "\033[33mdeploy service [$serviceName] on [$deployHost] start... \033[0m$sourceHostPre"
  echo -e "\033[32mdeploy info=======================================start \033[0m$sourceHostPre"
  echo "|buildId: [$buildId]$sourceHostPre"
  echo "|sourceIp: [$sourceIp]$sourceHostPre"
  echo "|deployHost: [$deployHost]$sourceHostPre"
  echo "|serviceName: [$serviceName]$sourceHostPre"
  echo "|imageName: [$imageName]$sourceHostPre"
  echo "|imageTag: [$imageTag]$sourceHostPre"
  echo "|env AGENT_PATH: [$AGENT_PWD]$sourceHostPre"
  echo -e "\033[32mdeploy info=======================================end \033[0m$sourceHostPre"

  # pull image
  echo -e "\033[32mpull image $imageName:$imageTag start \033[0m$sourceHostPre"
  pullResp=$(docker pull $imageName:$imageTag 2>&1)
  echo "$pullResp"|sed 's/$/&'"$sourceHostPre"'/g'
  # to latest
  echo -e "\033[32mtag to latest image\033[0m $sourceHostPre"
  echo "[$imageName:$imageTag => $imageName:latest]$sourceHostPre"
  ## tag to latest images
  docker tag $imageName:$imageTag $imageName:latest
  images=$(docker images | grep $(docker images | grep $imageName | grep $imageTag | awk '{print$3}') 2>&1)
  echo "$images"|sed 's/$/&'"$sourceHostPre"'/g'
  ## deploy
  res=$(deployResp $serviceName $AGENT_PWD/yamlDir/$serviceName.yml 2>&1)
  pss=$(docker ps | grep -w "$serviceName$" 2>&1)
  if [ $? -ne 0 ]; then
    echo "$res" |sed 's/$/&'"$sourceHostPre"'/g'
    echo -e "\033[31mdeploy faild \033[0m$sourceHostPre"
    echo -e "\033[32m=========> run info\033[0m$sourceHostPre"
    echo "$pss" | sed 's/$/&'"$sourceHostPre"'/g'
    echo $serviceName" [REMOTE_DEPLOY_END]:1:$buildId:$sourceIp"
    return 1
  else
    echo "$res" |sed 's/$/&'"$sourceHostPre"'/g'
    echo -e "\033[32m=========> run info\033[0m$sourceHostPre"
    echo "$pss" | sed 's/$/&'"$sourceHostPre"'/g'
    echo -e "\033[32mdeploy service $serviceName successful\033[0m$sourceHostPre"
    echo $serviceName" [REMOTE_DEPLOY_END]:0:$buildId:$sourceIp"
    return 0
  fi

}


case $1 in
   "getServerInfoResp" | "build" | "deployResp" | "stopResp" | "restartResp" | "rmContainerResp" | "getYamlFile" |"getYamlFileResp" | "syncNetworkResp" | "remoteDeployResp") eval $@ ;;
   *) echo "invalid command $1" ;;
esac
