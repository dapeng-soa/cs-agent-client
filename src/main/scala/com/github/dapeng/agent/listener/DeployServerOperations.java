package com.github.dapeng.agent.listener;

import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import java.util.concurrent.BlockingQueue;

/**
 * @author duwupeng
 * @date
 */
public class DeployServerOperations implements Emitter.Listener{
    Socket socket;
    BlockingQueue<String> queue;
    public DeployServerOperations(BlockingQueue<String> queue , Socket socket){
        this.socket = socket;
        this.queue = queue;
    }
    @Override
    public void call(Object... objects) {
        String agentEvent = (String)objects[0];
        try {
            queue.put(agentEvent);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
