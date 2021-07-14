package com.fa993.hydra.impl;

import com.fa993.hydra.api.Parcel;
import com.fa993.hydra.api.ReceiverConnection;
import com.fa993.hydra.core.Transaction;
import com.fa993.hydra.core.TransactionResult;

import java.io.*;
import java.net.*;
import java.util.function.Function;

public class SocketReceiverConnection implements ReceiverConnection {

    private ServerSocket serverSocket;
    private URL serverURL;
    private ExchangeSpec spec;

    public SocketReceiverConnection(String myServerURL, ExchangeSpec spec) {
        try {
            this.serverURL = new URL(myServerURL);
            this.spec = spec;
            this.serverSocket = new ServerSocket(this.serverURL.getPort(), 1000, InetAddress.getByName(this.serverURL.getHost()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Transaction receive(int timeout, Function<Parcel, Boolean> validator) {
        try {
            this.serverSocket.setSoTimeout(timeout);
            Socket so = this.serverSocket.accept();
            so.setTcpNoDelay(true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(so.getInputStream()));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(so.getOutputStream()));
            char prep = (char) reader.read();
            if (spec.containsReverseMarker(prep)) {
                writer.write('0');
            } else {
                writer.write('1');
            }
            writer.flush();
            String collection = reader.readLine();
            Parcel receivedParcel = (Parcel) spec.decode(collection, spec.getReverseMarkerFor(prep));
            if (validator.apply(receivedParcel)) {
                char c = spec.getMarkerFor(receivedParcel.getClass());
                writer.write(c);
                writer.flush();
                return new Transaction(receivedParcel, spec.getTransactionResultFor(c));
            } else {
                writer.write('0');
                writer.flush();
                return new Transaction((Parcel) null, TransactionResult.VETOED);
            }
        } catch (SocketTimeoutException e) {
            return new Transaction((Parcel) null, TransactionResult.TIMEOUT);
        } catch (Exception e) {
            e.printStackTrace();
            return new Transaction((Parcel) null, TransactionResult.FAILURE);
        }
    }
}
