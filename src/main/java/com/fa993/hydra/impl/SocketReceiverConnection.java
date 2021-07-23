package com.fa993.hydra.impl;

import com.fa993.hydra.api.ReceiverConnection;
import com.fa993.hydra.core.Command;
import com.fa993.hydra.core.Token;
import com.fa993.hydra.core.Transaction;
import com.fa993.hydra.core.TransactionResult;
import com.fa993.hydra.exceptions.ObjectPoolExhaustedException;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class SocketReceiverConnection implements ReceiverConnection, AutoCloseable {

    private static final int poolTimeout = 5000;

    private ServerSocket serverSocket;
    private URL serverURL;
    private ExchangeSpec spec;

    private Set<Socket> connectedSockets;

    private LinkedBlockingQueue<PartiallyProcessedSocket> eventQueue;

    private LinkedBlockingQueue<Transaction> collectedStates;
    private LinkedBlockingQueue<Transaction> collectedCommands;
    private LinkedBlockingQueue<Transaction> collectedTokens;
    private ExecutorService executorService;

    private Function<Token, Boolean> validator;
    private final byte[] centralBuffer;


    public SocketReceiverConnection(String myServerURL, ExchangeSpec spec) {
        this.centralBuffer = new byte[4096];
        try {
            this.serverURL = new URL(myServerURL);
            this.spec = spec;
            this.serverSocket = new ServerSocket(this.serverURL.getPort(), 1000, InetAddress.getByName(this.serverURL.getHost()));
            this.connectedSockets = new HashSet<>();
            this.eventQueue = new LinkedBlockingQueue<>();
            this.collectedCommands = new LinkedBlockingQueue<>();
            this.collectedStates = new LinkedBlockingQueue<>();
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
                        this.connectedSockets.add(s);
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

    @Override
    public void configureValidatorForState(Function<Token, Boolean> validator) {
        this.validator = validator;
    }

    @Override
    public Transaction receiveToken(int timeout) throws ObjectPoolExhaustedException {
        try {
            Transaction t = this.collectedTokens.poll(timeout, TimeUnit.MILLISECONDS);
            if (t == null) {
                throw new Exception("Timeout");
            } else return t;
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            Transaction t0 = Transaction.getTransaction(poolTimeout);
            t0.setParcel(null);
            t0.setResult(TransactionResult.FAILURE);
            return t0;
        } catch (Exception e) {
            Transaction t0 = Transaction.getTransaction(poolTimeout);
            t0.setParcel(null);
            t0.setResult(TransactionResult.TIMEOUT);
            return t0;
        }
    }

    @Override
    public Transaction receiveCommand() throws ObjectPoolExhaustedException {
        try {
            return this.collectedCommands.take();
        } catch (InterruptedException e) {
            Transaction t = Transaction.getTransaction(poolTimeout);
            t.setResult(TransactionResult.FAILURE);
            t.setParcel(null);
            return t;
        }
    }

    private void handleProtocol(Socket so, byte[] buffer) {
        BufferedReader reader;
        BufferedWriter writer;
        InputStream is;
        OutputStream os;
        try {
            is = so.getInputStream();
            os = so.getOutputStream();
            reader = new BufferedReader(new InputStreamReader(so.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(so.getOutputStream()));
        } catch (Exception ex) {
            return;
        }
        while (!so.isClosed()) {
            Transaction t2 = null;
            boolean b1 = false;
            boolean b2 = false;
            try {
//                int x = reader.read();
//                if (x == -1) {
//                    so.close();
//                    //end-of-stream... next while check should break this loop
//                    continue;
//                }
//                char prep = (char) x;
//                if (spec.containsReverseMarker(prep)) {
//                    writer.write(this.spec.getCharacterSuccess());
//                    writer.flush();
//                } else {
//                    writer.write(this.spec.getCharacterFailure());
//                    writer.flush();
//                    //protocol break... so reset
//                    continue;
//                }
//                is.read(this.commandBuffer, 0, 4);
//                //assemble read
//                is.read(this.commandBuffer, 0, /**gotten length**/);
//                this.
                t2 = Transaction.getTransaction(poolTimeout);
                Token t = Token.getToken(poolTimeout);
                this.spec.readBuffer(is, buffer, 0, 1);
                if (b1 = (buffer[0] == this.spec.getByteMarkerFor(Token.class))) {
                    this.spec.readBuffer(is, buffer, 1, 12);
                    this.spec.deserializeToken(buffer, 1, t);
                    if (this.validator.apply(t)) {
                        t2.setParcel(t);
                        t2.setResult(TransactionResult.SUCCESS);
                        os.write(this.spec.getSuccessByte());
                    } else {
                        //only state objects can be vetoed so this works
                        t2.setParcel(null);
                        t2.setResult(TransactionResult.VETOED);
                        os.write(this.spec.getFailureByte());
                    }
                    os.flush();
                } else if (b2 = (buffer[0] == this.spec.getByteMarkerFor(Command.class))) {
                    //get length of command
                    this.spec.readBuffer(is, buffer, 1, 4);
                    //handle framing and command ops
                    //TODO
                }
//                String collection = reader.readLine();
//                Parcel receivedParcel = (Parcel) spec.decode(collection, spec.getReverseMarkerFor(prep));
//                char c = spec.getMarkerFor(receivedParcel.getClass());
//                if (c == spec.getMarkerFor(State.class)) {
//                    if (this.validator.apply((State) receivedParcel)) {
//                        this.collectedStates.add(new Transaction<State>((State) receivedParcel, TransactionResult.SUCCESS));
//                        writer.write(c);
//                    } else {
//                        //only state objects can be vetoed so this works
//                        this.collectedStates.add(new Transaction((State) null, TransactionResult.VETOED));
//                        writer.write(this.spec.getCharacterFailure());
//                    }
//                } else if (c == spec.getMarkerFor(Command.class)) {
//                    this.collectedCommands.add(new Transaction<Command>((Command) receivedParcel, TransactionResult.SUCCESS));
//                    writer.write(c);
//                }
//                writer.flush();
            } catch (Exception ex) {
                ex.printStackTrace();
                try {
                    so.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // do nothing
                //if end-of-stream has been reached and socket is closed then the loop will exit gracefully at the next while check
            }
            //do all the additions here
            if (t2.getResult() != null) {
                if (b1) {
                    this.collectedTokens.add(t2);
                } else if (b2) {
                    this.collectedCommands.add(t2);
                }
            }
        }
    }

    @Override
    public void close() throws Exception {
//        this.centralBuffer.
    }

}
