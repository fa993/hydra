package com.fa993.impl;

import com.fa993.core.TransactionResult;
import com.fa993.api.TransmitterConnection;
import com.fa993.core.State;
import com.fa993.misc.Utils;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;

public class SocketTransmitterConnection implements TransmitterConnection {

    private String myServer;

    public SocketTransmitterConnection(String myServerURL) {
        this.myServer = myServerURL;
    }

    @Override
    public TransactionResult send(String serverURL, State state) {
        try {
            URL urlTo = new URL(serverURL);
            Socket socket = new Socket(InetAddress.getByName(urlTo.getHost()), urlTo.getPort());
            socket.setTcpNoDelay(true);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            writer.write(Utils.obm.writeValueAsString(state) + "\n");
            writer.flush();
            char c = (char) new BufferedReader(new InputStreamReader(socket.getInputStream())).read();
            socket.close();
            switch (c){
                case '0': return TransactionResult.SUCCESS;
                case '1': return TransactionResult.VETOED;
                default: return TransactionResult.FAILURE;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return TransactionResult.FAILURE;
        }
    }
}
