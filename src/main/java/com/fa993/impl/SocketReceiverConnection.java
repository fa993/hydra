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
            this.serverSocket.setSoTimeout(timeout);
            Socket so = this.serverSocket.accept();
            so.setTcpNoDelay(true);
            BufferedReader str = new BufferedReader(new InputStreamReader(so.getInputStream()));
            String collection = str.readLine();
            BufferedWriter wr = new BufferedWriter(new OutputStreamWriter(so.getOutputStream()));
            wr.write('0');
            wr.flush();
            return Optional.ofNullable(Utils.obm.readValue(collection, State.class));
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }
}
