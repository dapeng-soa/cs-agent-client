## 打包
```sbtshell
sbt dist
```
- 生成可执行文件`agentClient`至`target`下

## 确保可执行
将打包所得可执行文件`agentClient`,`shell`目录下`agent.sh`、`startup.sh`文件复制到同一目录：
```sbtshell
agent_client
    |- agentClient #核心启动程序
    |- agent.sh #执行指令的脚本
    |- startup.sh #启动脚本
```
## 启动客户端
```bash
sh startup.sh http://127.0.0.1:6666
```
- 其中`http://127.0.0.1:6666`为远程地址
- 启动后日志存放于`logs`目录下




