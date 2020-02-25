package com.github.dapeng.agent.client;

import com.github.dapeng.socket.enums.EventType;
import io.socket.client.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;

/**
 * Created by duwupeng on 16/10/18.
 */
public class CmdExecutor implements Runnable{
    private static Logger LOGGER = LoggerFactory.getLogger(CmdExecutor.class);
    public BlockingQueue queue;
    Socket socket;
    public CmdExecutor(BlockingQueue queue,Socket socket) {
        this.queue = queue;
        this.socket=socket;
    }

    @Override
    public void run() {
            while(true) {
                try {
                    String  event = (String)queue.take();

                    String oriEvent = event.split(" ")[0];

                    if (oriEvent.equals(EventType.GET_SERVER_INFO_RESP().name())||oriEvent.equals(EventType.GET_YAML_FILE().name())){
                        DeployServerShellInvoker.executeShell(socket, event);
                    }else {
                        socket.emit(oriEvent,"[started]");
                        socket.emit(oriEvent, event);
                        DeployServerShellInvoker.executeShell(socket, event);
                        socket.emit(oriEvent,"[end]");
                    }
                } catch (Exception ex) {
                   ex.printStackTrace();
                }
            }
    }
}
