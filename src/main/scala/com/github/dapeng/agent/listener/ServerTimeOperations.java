package com.github.dapeng.agent.listener;

import com.github.dapeng.socket.enums.EventType;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import java.util.concurrent.BlockingQueue;

public class ServerTimeOperations implements Emitter.Listener {
    Socket socket;
    BlockingQueue queue;

    public ServerTimeOperations(BlockingQueue queue , Socket socket){
        this.socket = socket;
        this.queue = queue;
    }
    @Override
    public void call(Object... objects) {
        try{
            String serviceName = (String)objects[0];

            queue.put(EventType.GET_SERVER_INFO().name() + " " + serviceName);
        }catch (Exception e){
            e.printStackTrace();
        }

    }
}
