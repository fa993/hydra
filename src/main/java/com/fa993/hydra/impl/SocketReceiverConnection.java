package com.fa993.hydra.impl;

import com.fa993.hydra.core.State;
import com.fa993.hydra.misc.Utils;
import com.fa993.hydra.api.ReceiverConnection;
import com.fa993.hydra.core.Transaction;
import com.fa993.hydra.core.TransactionResult;

import java.io.*;
import java.net.*;
import java.util.function.Function;

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
    public Transaction receive(int timeout, Function<State, Boolean> validator) {
        try {
            this.serverSocket.setSoTimeout(timeout);
            Socket so = this.serverSocket.accept();
            so.setTcpNoDelay(true);
            BufferedReader str = new BufferedReader(new InputStreamReader(so.getInputStream()));
            String collection = str.readLine();
            State receivedState = Utils.obm.readValue(collection, State.class);
            BufferedWriter wr = new BufferedWriter(new OutputStreamWriter(so.getOutputStream()));
            if(!validator.apply(receivedState)){
                wr.write('1');
                wr.flush();
                return new Transaction((State) null, TransactionResult.VETOED);
            } else {
                wr.write('0');
                wr.flush();
                return new Transaction(receivedState, TransactionResult.SUCCESS);
            }
        } catch (SocketTimeoutException e) {
            return new Transaction((State) null, TransactionResult.TIMEOUT);
        } catch (Exception e) {
            e.printStackTrace();
            return new Transaction((State) null, TransactionResult.FAILURE);
        }
    }
}
