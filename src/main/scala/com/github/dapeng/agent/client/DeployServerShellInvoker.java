package com.github.dapeng.agent.client;

import com.github.dapeng.socket.enums.EventType;
import io.socket.client.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import static com.github.dapeng.socket.SystemParas.COMMAS;
import static com.github.dapeng.socket.SystemParas.SHELLNAME;


/**
 * @author duwupeng on 2016-08-10
 */
public class DeployServerShellInvoker {

    private static Logger LOGGER = LoggerFactory.getLogger(DeployServerShellInvoker.class);

    public static void executeShell(Socket socket, String event) throws Exception {
        BufferedReader br = null;
        BufferedWriter wr = null;

        try {
            LOGGER.info("execute command:" + SHELLNAME + " " + event);
            Runtime runtime = Runtime.getRuntime();
            Process process;


            String[] cmd = null;
            String realCmd = null;
            if (event.indexOf(COMMAS) != -1) {
                realCmd = SHELLNAME + event.replaceAll(COMMAS, " ").replace("*", "");
            } else {
                realCmd = SHELLNAME + " " + event;
            }

            cmd = new String[]{"/bin/sh", "-c", realCmd};

            // 执行Shell命令
            process = runtime.exec(cmd);

            br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            wr = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));


            String inline;
            while ((inline = br.readLine()) != null) {
                processInlineToSendEvent(event, socket, inline);
                //LOGGER.info(inline);
            }
            br.close();

            br = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((inline = br.readLine()) != null) {
                processInlineToSendEvent(EventType.ERROR_EVENT().name(), socket, inline);
                //LOGGER.info(inline);
            }

            if (process != null) {
                process.waitFor();
            }

        } catch (Exception ioe) {
            ioe.printStackTrace();
        } finally {
            if (br != null)
                br.close();
            if (wr != null)
                wr.close();
        }
    }

    private static void processInlineToSendEvent(String oriEvent, Socket socket, String inline) {
        //LOGGER.info("agent_bash start to send " + oriEvent + " socket: " + socket.id() + "conetnt: " + inline);

        String[] args = oriEvent.split(" ");
        String oriCmd = args[0];
        EventType event = EventType.findByLabel(oriCmd);

        //LOGGER.info("received oriEvent: " + event.name());
        if (EventType.GET_SERVER_INFO_RESP().name().equals(oriCmd)) {
            socket.emit(EventType.GET_SERVER_INFO_RESP().name(), socket.id() + ":" + inline);
        } else if (EventType.GET_YAML_FILE().name().toUpperCase().equals(oriCmd.toUpperCase())) {
            socket.emit(EventType.GET_YAML_FILE_RESP().name(), inline);
        } else if (EventType.BUILD().name().toUpperCase().equals(oriCmd.toUpperCase())) {
            socket.emit(EventType.BUILD_RESP().name(), inline);
        } else {
            socket.emit(event.name(), inline);
        }
    }

}

