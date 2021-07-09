package com.fa993.impl;

import com.fa993.api.ReceiverConnection;
import com.fa993.core.State;
import com.fa993.core.Transaction;
import com.fa993.core.TransactionResult;
import com.fa993.misc.Utils;

import java.io.*;
import java.net.*;
import java.util.Optional;
import java.util.function.Function;
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
    public Transaction receive(int timeout, Function<State, Boolean> validator) {
        //TODO fix this
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
            e.printStackTrace();
            return new Transaction((State) null, TransactionResult.TIMEOUT);
        } catch (Exception e) {
            e.printStackTrace();;
            return new Transaction((State) null, TransactionResult.FAILURE);
        }
    }
}
