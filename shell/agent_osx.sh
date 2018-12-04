#!/bin/bash
# only run in Mac OSX
getServerInfoResp(){
    if [ $# -lt 2 ];then
        echo "invalid cmd...please input your request [serviceName],[serviceName.yml]"
        exit 1
    fi
   ip=$(ifconfig en0|grep "inet "|awk '{print $2}')


   if [ -e "$2" ];then
        time=`stat -f "%B %N" $2 | awk '{print$1}'`
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
        echo  "\033[33m invalid cmd...please input your request [serviceName],[serviceName.yml] \033[0m"
        exit 1
    fi
  serviceName="$1"
  ymlFile="$2"

  if [ ! -f "$ymlFile" ]; then
    echo "找不到对应的$ymlFile"
  else
    docker-compose -p $serviceName -f $ymlFile up -d
    if [ $? -ne 0 ]; then
        echo  "\033[31m update $serviceName failed \033[0m"
        echo  "\033[33m done \033[0m"
    else
        echo  "\033[32m update successful!!! \033[0m"
    fi
  fi
}

stopResp() {
  ip=$(ifconfig en0|grep "inet "|awk '{print $2}')
  echo $@
  echo  "\033[33m $ip stopping $1 \033[0m"
  docker stop $1
  if [ $? -ne 0 ]; then
    echo   "\033[31m stop $1 fail \033[0m"
  else
    echo  "\033[32m stop $1 success \033[0m"
  fi
}

restartResp() {
   ip=$(ifconfig en0|grep "inet "|awk '{print $2}')
   echo  "\033[33m $ip restarting $1 \033[0m"
  docker restart $1
  if [ $? -ne 0 ]; then
    echo   "\033[31m restart $1 fail \033[0m"
  else
    echo  "\033[32m restart $1 success \033[0m"
  fi
}

rmContainerResp() {
   ip=$(ifconfig en0|grep "inet "|awk '{print $2}')
  echo $@
   echo "\033[33m $ip rm Container $1 \033[0m"
  docker rm $1
  if [ $? -ne 0 ]; then
    echo "\033[31m rm Container $1 fail \033[0m"
  else
    echo "\033[32m rm Container $1 success \033[0m"
  fi
}

getYamlFileResp() {
   cat $1
}
getYamlFile() {
   cat $1
}

syncNetworkResp() {
  ip=$(ifconfig en0|grep "inet "|awk '{print $2}')
  networkName="$1"
  driver="$2"
  subnet="$3"
  opt="$4"
  docker network create -d=$driver --subnet=$subnet -o=$opt $networkName
  if [ $? -ne 0 ]; then
    echo   "\033[31m $ip create network $networkName fail \033[0m"
  else
    echo  "\033[32m $ip create network $networkName success \033[0m"
  fi
}

build() {
	# build info start
	serviceName=$1
	projectUrl=$2
	serviceBranch=$3
	imageName=$4
	realService=$5
	cmd=`echo ${@:6}`
	echo -e "\033[33mbuild service [$serviceName] [$serviceBranch] start... \033[0m"
	echo -e "\033[32mbuild info=======================================start \033[0m"
	echo "|"
	echo "| build realService:[$realService]"
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
		echo -e  "\033[31m 目录不存在,请添加AGENT_HOME环境变量指定agent目录: $AGENT_PWD, 退出 \033[0m"
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
	    echo "$1=$serviceName" >> .build.cache.ini

	    ## tag to latest images
	    echo "\033[32m tag to latest image \033[0m"
	    docker tag $imageName:$newGitId $imageName:latest
	    ## if is realService ,deploy service
	    if [ "$serviceName" = "$realService" ]; then
	      echo "\033[32mbuild is realService , deploy realService \033[0m"
	      res=$(deployResp $serviceName $AGENT_PWD/yamlDir/$serviceName.yml 2>&1)
	      if [ $? -ne 0 ]; then
	        echo $res
			echo -e "\033[31mdeploy faild \033[0m"
			echo $serviceName" BUILD_END:1"
			return 1
		  fi
		else
		  echo "\033[33m[$serviceName] not is realService , skip deploy\033[0m"
	    fi
	else
		echo "构建失败， 跳过更新gitid"
	fi
	echo $1" BUILD_END:$BUILD_STATUS"
}

case $1 in
   "getServerInfoResp" | "build" | "deployResp" | "stopResp" | "restartResp" | "rmContainerResp" | "getYamlFile" |"getYamlFileResp" | "syncNetworkResp") eval $@ ;;
   *) echo "invalid command $1" ;;
esac
