#!/bin/bash
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
  ip=$(ifconfig en0|grep "inet "|awk '{print $2}')
  echo $@
  echo " $ip stopping $1"
  docker stop $1
}

restartResp() {
   ip=$(ifconfig en0|grep "inet "|awk '{print $2}')
   echo " $ip restarting $1"
  docker restart $1
}

getYamlFileResp() {
   cat $1
}
getYamlFile() {
   cat $1
}

build() {
    echo "ori cmd: $@"
    echo "serviceName: $1"
    echo "projectUrl: $2"
    echo "serviceBranch: $3"
    cmd=`echo ${@:4}`
    echo "cmd: $cmd"
    projectRootName=`echo ${2##*/} | cut -d . -f 1`
    echo "projectGitName: "$projectRootName
    WORKSPACE=`echo $COMPOSE_WORKSPACE`
    if [ ! -d "$WORKSPACE" ];
    then
        echo "目录不存在: /data/jack/agent_test/workspace, 退出"
        return 1
    fi

    cd $WORKSPACE
    if [ ! -d $projectRootName ];
    then
        echo "项目不存在, 拉取项目: $2"
        git clone $2
    else
        echo "项目已存在, 执行构建指令"
    fi

    cd /home/jack/agent_test/workspace/$projectRootName

    #判断cmd是否包含api指令，有的话，需要更新.build_api.cache.ini, 否则更新.build.cache.ini
    echo "执行指令: $cmd"
    if [[ $cmd =~ "api" ]]
    then
        echo "指令包含api，更新.build_api.cache.ini"
        #fixme, 添加环境变量
        cd $AGENT_HOME
        if [ ! -f ".build_api.cache.ini" ];
        then
            echo ".build_api.cache.ini 文件不存在, 新建"
            touch .build_api.cache.ini
        else
            echo ".build_api.cache.ini 文件已存在"
        fi

        oldGitId=`cat .build_api.cache.ini | grep $1 | awk -F "=" '{print $2}'`
        newGitId=`git rev-parse --shot HEAD`
        echo 'oldGitId: ${oldGitId}, newGitId: $newGitId'
        if [ $newGitId = $oldGitId ];
        then
            echo "gitId 一致，不需要重新构建，跳过..."
        else
            #remove service old gitid

            echo "gitId不一致, 删除旧的gitid, 重新构建"
            sed -i "/^$1/d" .build_api.cache.ini
            #add service new gitid at last line of .build_api.cache.ini
            echo "$1=$gitid" >> .build_api.cache.ini

            cd $COMPOSE_WORKSPACE
            echo "执行指令: $cmd"
            eval `$cmd`
        fi
    else
        echo "不包含api"
        cd $AGENT_HOME
        if [ ! -f ".build.cache.ini" ];
        then
            echo ".build.cache.ini 文件不存在，新建"
            touch .build.cache.ini
        else
            echo ".build.cache.ini 文件已存在"
        fi

        oldGitId=`cat .build.cache.ini | grep $1 | awk -F "=" '{print $2}'`
        newGitId=`git rev-parse --shot HEAD`
        echo 'oldGitId: ${oldGitId}, newGitId: $newGitId'
        if [ $newGitId = $oldGitId ];
        then
            echo "gitId 一致，不需要重新构建，跳过..."
        else
            #remove service old gitid
            echo "删除旧的gitid"
            sed -i "/^$1/d" .build_api.cache.ini
            #add service new gitid at last line of .build_api.cache.ini
            echo "$1=$gitid" >> .build_api.cache.ini

            cd $COMPOSE_WORKSPACE
            echo "执行指令: $cmd"
            eval `$cmd`
        fi
    fi
}


case $1 in
   "getServerInfoResp" | "build" | "deployResp" | "stopResp" | "restartResp" | "getYamlFile" |"getYamlFileResp") eval $@ ;;
   *) echo "invalid command $1" ;;
esac
