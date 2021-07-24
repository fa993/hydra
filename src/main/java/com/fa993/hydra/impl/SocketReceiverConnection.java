package com.fa993.hydra.impl;

import com.fa993.hydra.api.ReceiverConnection;
import com.fa993.hydra.core.Command;
import com.fa993.hydra.core.Token;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class SocketReceiverConnection implements ReceiverConnection, AutoCloseable {

    private static final int poolTimeout = 5000;

    private ServerSocket serverSocket;
    private URL serverURL;
    private ExchangeSpec spec;

    private LinkedBlockingQueue<Command> collectedCommands;
    private LinkedBlockingQueue<Token> collectedTokens;
    private ExecutorService executorService;

    private final byte[] centralBuffer;


    public SocketReceiverConnection(String myServerURL, ExchangeSpec spec) {
        this.centralBuffer = new byte[4096];
        try {
            this.serverURL = new URL(myServerURL);
            this.spec = spec;
            this.serverSocket = new ServerSocket(this.serverURL.getPort(), 1000, InetAddress.getByName(this.serverURL.getHost()));
            this.collectedCommands = new LinkedBlockingQueue<>();
            this.collectedTokens = new LinkedBlockingQueue<>();
            this.executorService = Executors.newSingleThreadExecutor((r) -> {
                Thread t = new Thread(r, "HydraTCP-exec-" + System.currentTimeMillis());
                t.setDaemon(true);
                return t;
            });
            Thread t1 = new Thread(() -> {
                while (!this.serverSocket.isClosed()) {
                    try {
                        Socket s = this.serverSocket.accept();
                        s.setTcpNoDelay(true);
                        this.executorService.execute(() -> handleProtocol(s, this.centralBuffer));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, "HydraTCP-Connection-Dispatcher-Thread");
            t1.setDaemon(true);
            t1.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    @Override
//    public Transaction receive(int timeout, Function<Parcel, Boolean> validator) {
//        long t1 = System.currentTimeMillis();
//        try {
//            PartiallyProcessedSocket st = this.eventQueue.poll(timeout, TimeUnit.MILLISECONDS);
//            if (st != null && st.getFirstByte() != -1) {
//                Transaction tr = handleHalfTransaction(validator, st.getS(), st.getFirstByte());
//                this.executorService.execute(() -> waitForFirstCharacter(st.getS()));
//                return tr;
//            } else if (st != null && st.getFirstByte() == -1) {
//                //should never happen as it has already been handled in the waiting function
//                throw new Exception();
//            } else {
//                return new Transaction((Parcel) null, TransactionResult.TIMEOUT);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//            return new Transaction((Parcel) null, TransactionResult.FAILURE);
//        }
//    }

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

//    private Transaction handleHalfTransaction(Function<Parcel, Boolean> validator, Socket so, int fr) throws Exception {
//        BufferedReader reader = new BufferedReader(new InputStreamReader(so.getInputStream()));
//        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(so.getOutputStream()));
//        char prep = (char) fr;
//        if (spec.containsReverseMarker(prep)) {
//            writer.write(this.spec.getCharacterSuccess());
//            writer.flush();
//        } else {
//            writer.write(this.spec.getCharacterFailure());
//            writer.flush();
//            throw new Exception();
//        }
//        String collection = reader.readLine();
//        Parcel receivedParcel = (Parcel) spec.decode(collection, spec.getReverseMarkerFor(prep));
//        if (validator.apply(receivedParcel)) {
//            char c = spec.getMarkerFor(receivedParcel.getClass());
//            writer.write(c);
//            writer.flush();
//            return new Transaction(receivedParcel, TransactionResult.SUCCESS);
//        } else {
//            writer.write(this.spec.getCharacterSuccess());
//            writer.flush();
//            return new Transaction((Parcel) null, TransactionResult.VETOED);
//        }
//    }

//    private void waitForFirstCharacter(Socket s) {
//        try {
//            int i = s.getInputStream().read();
//            if (i != -1) {
//                this.eventQueue.add(new PartiallyProcessedSocket(s, i));
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

    @Override
    public Token receiveToken(int timeout) {
        try {
            return this.collectedTokens.poll(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            //wrong... will never interrupt this thread.... i think
            ex.printStackTrace();
            return null;
        }
    }

    @Override
    public Command receiveCommand() {
        try {
            return this.collectedCommands.take();
        } catch (InterruptedException e) {
            //wrong... will never interrupt this thread.... i think
            e.printStackTrace();
            return null;
        }
    }

    private void handleProtocol(Socket so, byte[] buffer) {
        InputStream is;
        OutputStream os;
        try {
            is = so.getInputStream();
            os = so.getOutputStream();
        } catch (Exception ex) {
            return;
        }
        while (!so.isClosed()) {
            Token t = null;
            Command c = null;
            try {
                this.spec.readBuffer(is, buffer, 0, 1);
                if (buffer[0] == this.spec.getByteMarkerFor(Token.class)) {
                    t = Token.getToken(poolTimeout);
                    this.spec.readBuffer(is, buffer, 1, 12);
                    this.spec.deserializeToken(buffer, 1, t);
                    os.write(this.spec.getSuccessByte());
                    os.flush();
                } else if (buffer[0] == this.spec.getByteMarkerFor(Command.class)) {
                    //get length of command
                    this.spec.readBuffer(is, buffer, 1, 4);
                    //handle framing and command ops
                    //TODO
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                try {
                    so.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            //do all the additions here
            if (t != null) {
                this.collectedTokens.add(t);
                t = null;
            }
            if (c != null) {
                this.collectedCommands.add(c);
                c = null;
            }
        }
    }

    @Override
    public void close() throws Exception {
//        this.centralBuffer.
    }

}
