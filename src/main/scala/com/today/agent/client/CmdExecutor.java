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

                    String oriEvent = event.split(" ")[0];

                    socket.emit(oriEvent,"[started]");
                    socket.emit(oriEvent, event);

                    DeployServerShellInvoker.executeShell(socket, event);

                    socket.emit(oriEvent,"[end]");
                } catch (Exception ex) {
                   ex.printStackTrace();
                }
            }
    }
}
