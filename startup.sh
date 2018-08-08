oldpid=`cat pid.txt`
echo killing Previous pid: ${oldpid}
kill ${oldpid}
echo runing app
sh agentClient $1 >> ./logs/agent_clinet.log 2>&1 &
echo "$!" > pid.txt
newpid=`cat pid.txt`
echo runing new app pid is : ${newpid}

