#!/usr/bin/env bash
if [ $# -lt 1 ];then
        echo "connect serverUrl not set ,Please set e.g. [http://127.0.0.1:6886]"
        exit 1
    fi
oldpid=`cat pid.txt`
echo killing Previous pid: ${oldpid}
kill ${oldpid}
echo runing app
sh agentClient $1 > /dev/null &
echo "$!" > pid.txt
newpid=`cat pid.txt`
echo runing new app pid is : ${newpid}

