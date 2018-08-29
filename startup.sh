oldpid=`cat pid.txt`
echo killing Previous pid: ${oldpid}
kill ${oldpid}
echo runing app
sh agentClient $1 > /dev/null &
echo "$!" > pid.txt
newpid=`cat pid.txt`
echo runing new app pid is : ${newpid}

