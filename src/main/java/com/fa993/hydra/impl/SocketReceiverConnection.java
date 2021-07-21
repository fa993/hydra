package com.fa993.hydra.impl;

import com.fa993.hydra.api.Parcel;
import com.fa993.hydra.api.ReceiverConnection;
import com.fa993.hydra.core.Transaction;
import com.fa993.hydra.core.TransactionResult;

import java.io.*;
import java.net.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class SocketReceiverConnection implements ReceiverConnection {

    private ServerSocket serverSocket;
    private URL serverURL;
    private ExchangeSpec spec;

    private Set<Socket> connectedSockets;

    private LinkedBlockingQueue<PartiallyProcessedSocket> eventQueue;
    private ExecutorService executorService;

    public SocketReceiverConnection(String myServerURL, ExchangeSpec spec) {
        try {
            this.serverURL = new URL(myServerURL);
            this.spec = spec;
            this.serverSocket = new ServerSocket(this.serverURL.getPort(), 1000, InetAddress.getByName(this.serverURL.getHost()));
            this.connectedSockets = new HashSet<>();
            this.eventQueue = new LinkedBlockingQueue<>();
            this.executorService = Executors.newCachedThreadPool((r) -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });
            Thread t1 = new Thread(() -> {
                while (!this.serverSocket.isClosed()) {
                    try {
                        Socket s = this.serverSocket.accept();
                        this.connectedSockets.add(s);
                        this.executorService.execute(() -> waitForFirstCharacter(s));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            t1.setDaemon(true);
            t1.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Transaction receive(int timeout, Function<Parcel, Boolean> validator) {
        long t1 = System.currentTimeMillis();
        try {
            PartiallyProcessedSocket st = this.eventQueue.poll(timeout, TimeUnit.MILLISECONDS);
            if (st != null && st.getFirstByte() != -1) {
                Transaction tr = handleHalfTransaction(validator, st.getS(), st.getFirstByte());
                this.executorService.execute(() -> waitForFirstCharacter(st.getS()));
                return tr;
            } else if (st != null && st.getFirstByte() == -1) {
                //should never happen as it has already been handled in the waiting function
                throw new Exception();
            } else {
                return new Transaction((Parcel) null, TransactionResult.TIMEOUT);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new Transaction((Parcel) null, TransactionResult.FAILURE);
        }
    }

//    private Transaction handleTransaction(Function<Parcel, Boolean> validator, Socket so) throws Exception {
//        BufferedReader reader = new BufferedReader(new InputStreamReader(so.getInputStream()));
//        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(so.getOutputStream()));
//        char prep = (char) reader.read();
//        if (spec.containsReverseMarker(prep)) {
//            writer.write(this.spec.getCharacterSuccess());
//        } else {
//            writer.write(this.spec.getCharacterFailure());
//        }
//        writer.flush();
//        String collection = reader.readLine();
//        Parcel receivedParcel = (Parcel) spec.decode(collection, spec.getReverseMarkerFor(prep));
//        if (validator.apply(receivedParcel)) {
//            char c = spec.getMarkerFor(receivedParcel.getClass());
//            writer.write(c);
//            writer.flush();
//            return new Transaction(receivedParcel, spec.getTransactionResultFor(c));
//        } else {
//            writer.write(this.spec.getCharacterSuccess());
//            writer.flush();
//            return new Transaction((Parcel) null, TransactionResult.VETOED);
//        }
//    }

    private Transaction handleHalfTransaction(Function<Parcel, Boolean> validator, Socket so, int fr) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(so.getInputStream()));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(so.getOutputStream()));
        char prep = (char) fr;
        if (spec.containsReverseMarker(prep)) {
            writer.write(this.spec.getCharacterSuccess());
            writer.flush();
        } else {
            writer.write(this.spec.getCharacterFailure());
            writer.flush();
            throw new Exception();
        }
        String collection = reader.readLine();
        Parcel receivedParcel = (Parcel) spec.decode(collection, spec.getReverseMarkerFor(prep));
        if (validator.apply(receivedParcel)) {
            char c = spec.getMarkerFor(receivedParcel.getClass());
            writer.write(c);
            writer.flush();
            return new Transaction(receivedParcel, spec.getTransactionResultFor(c));
        } else {
            writer.write(this.spec.getCharacterSuccess());
            writer.flush();
            return new Transaction((Parcel) null, TransactionResult.VETOED);
        }
    }

    private void waitForFirstCharacter(Socket s) {
        try {
            int i = s.getInputStream().read();
            if (i != -1) {
                this.eventQueue.add(new PartiallyProcessedSocket(s, i));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
