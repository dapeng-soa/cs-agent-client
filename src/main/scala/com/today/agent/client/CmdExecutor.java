package com.today.agent.client;

import com.github.dapeng.socket.enums.EventType;
import io.socket.client.Socket;

import java.util.concurrent.BlockingQueue;

/**
 * Created by duwupeng on 16/10/18.
 */
public class CmdExecutor implements Runnable{
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
                    System.out.println("Consumed Event " + event);

                    socket.emit(EventType.NODE_EVENT().name(),"[started]");
                    socket.emit(EventType.NODE_EVENT().name(), event);

                    DeployServerShellInvoker.executeShell(socket, event);

                    socket.emit(EventType.NODE_EVENT().name(),"[end]");
                } catch (Exception ex) {
                   ex.printStackTrace();
                }
            }
    }
}
