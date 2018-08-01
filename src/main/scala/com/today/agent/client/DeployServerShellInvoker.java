package com.today.agent.client;

import com.github.dapeng.socket.SystemParas;
import com.github.dapeng.socket.enums.EventType;
import io.socket.client.Socket;

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

    public static void executeShell(Socket socket, String event) throws Exception {
        BufferedReader br = null;
        BufferedWriter wr = null;

        try {
            System.out.println(" original event: " + event);
            System.out.println("execute command:" + SHELLNAME + " " + event);
            Runtime runtime = Runtime.getRuntime();
            Process process;

            String oriCmd = event.split(" ")[0];
            String[] cmd = null;
            String realCmd = null;
            if (event.indexOf(COMMAS) != -1) {
                realCmd = SHELLNAME + event.replaceAll(COMMAS, " ").replace("*", "");
            } else {
                realCmd = SHELLNAME + " " + event;
            }
            System.out.println("oriCmd:" + oriCmd);
            System.out.println("realCmd: " + realCmd);
            cmd = new String[]{"/bin/sh", "-c", realCmd};

            // 执行Shell命令
            process = runtime.exec(cmd);

            br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            wr = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));


            String inline;
            while ((inline = br.readLine()) != null) {
                processInlineToSendEvent(oriCmd, socket, inline);
                System.out.println(inline);
            }
            br.close();

            br = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((inline = br.readLine()) != null) {
                System.out.println(inline);
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
        if (EventType.GET_SERVER_TIME().name().equals(oriEvent)) {
            socket.emit(EventType.SERVER_TIME().name(), socket.id() + ":" + inline);
        } else {
            socket.emit(EventType.WEB_EVENT().name(), inline);
        }
    }

}

