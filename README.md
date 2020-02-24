# cs-agent-client

[dapeng-config-server](https://github.com/dapeng-soa/dapeng-config-server) 的socket执行客户端，常部署在宿主机上接收指令

## 确保服务端已启动

参考: [cs-agent-server](https://github.com/dapeng-soa/cs-agent-server)

## 下载打包好的程序包📦
点击进入下载: [agent_client_2.2.1.tar.gz](https://github.com/dapeng-soa/cs-agent-client/releases/tag/2.2.1)
解压后目录结构如下:

```shell
agent_client
    |- agentClient #核心启动程序
    |- agent.sh #执行指令的脚本
    |- startup.sh #启动脚本
```

### 启动客户端
```bash
sh startup.sh http://127.0.0.1:6666
```
- 其中`http://127.0.0.1:6666`为[cs-agent-server](https://github.com/dapeng-soa/cs-agent-server)远程地址
- 启动后日志存放于`logs`目录下




