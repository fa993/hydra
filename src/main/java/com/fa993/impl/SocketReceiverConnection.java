package com.fa993.impl;

import com.fa993.api.ReceiverConnection;
import com.fa993.core.State;
import com.fa993.misc.Utils;

import java.io.*;
import java.net.*;
import java.util.Optional;
import java.util.stream.Collectors;

public class SocketReceiverConnection implements ReceiverConnection {

    private ServerSocket serverSocket;
    private URL serverURL;

    public SocketReceiverConnection(String myServerURL) {
        try {
            this.serverURL = new URL(myServerURL);
            this.serverSocket = new ServerSocket(this.serverURL.getPort(), 1000, InetAddress.getByName(this.serverURL.getHost()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Optional<State> receive(int timeout) {
        //TODO fix this
        try {
            long t1 = System.currentTimeMillis();
            this.serverSocket.setSoTimeout(timeout);
            Socket so = this.serverSocket.accept();
            BufferedReader str = new BufferedReader(new InputStreamReader(so.getInputStream()));
            String collection = str.readLine();
            so.getOutputStream().write(0);
            so.getOutputStream().flush();
            so.close();
            if (collection.length() <= 1) {
                return receive(timeout - Long.valueOf(System.currentTimeMillis() - t1).intValue());
            } else {
                return Optional.ofNullable(Utils.obm.readValue(collection, State.class));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }
}
