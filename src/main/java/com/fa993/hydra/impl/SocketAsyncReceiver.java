package com.fa993.hydra.impl;

import com.fa993.hydra.api.Parcel;
import com.fa993.hydra.api.ReceiverConnection;
import com.fa993.hydra.core.Command;
import com.fa993.hydra.core.Transaction;
import com.fa993.hydra.core.TransactionResult;

import java.io.IOException;
import java.net.*;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.function.Function;

public class SocketAsyncReceiver implements ReceiverConnection {

    private URL serverURL;
    private ExchangeSpec spec;

    private ServerSocketChannel channel;
    private Selector selector;

    public SocketAsyncReceiver(String myServerURL, ExchangeSpec spec) {
        try {
            this.serverURL = new URL(myServerURL);
            this.spec = spec;
            ServerSocketChannel cha = ServerSocketChannel.open();
            cha.configureBlocking(false);
            cha.bind(new InetSocketAddress(serverURL.getHost(), serverURL.getPort()), 1000);
            Selector select = Selector.open();
            cha.register(select, SelectionKey.OP_ACCEPT);
            this.channel = cha;
            this.selector = select;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Transaction receive(int timeout, Function<Parcel, Boolean> validator) {
        long t1 = System.currentTimeMillis();
        int difference;
        try {
            while ((difference = (int) (System.currentTimeMillis() - t1)) < timeout) {
                int readyKeys = this.selector.select(timeout - difference);
                if (readyKeys != 0) {
                    //process only one key
                    Iterator<SelectionKey> iterator = this.selector.selectedKeys().iterator();
                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        if (key.isAcceptable()) {
                            register(this.selector, this.channel);
                        }
                        if (key.isReadable()) {
                            SocketChannel chan = (SocketChannel) key.channel();
                            Transaction t = handleTransaction(chan, validator);
                            if (t != null) return t;
                        }
                        iterator.remove();
                    }
                }
            }
            return new Transaction((Parcel) null, TransactionResult.TIMEOUT);
        } catch (Exception e) {
            e.printStackTrace();
            return new Transaction((Parcel) null, TransactionResult.FAILURE);
        }
    }

    private Transaction handleTransaction(SocketChannel channel, Function<Parcel, Boolean> validator) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        if (!spec.readBuffer(channel, buffer, 2)) return null;
        char c = buffer.getChar(0);
        ((Buffer)buffer).clear();
        boolean b1 = spec.containsReverseMarker(c);
        if (b1) {
            buffer.putChar(0, this.spec.getCharacterSuccess());
        } else {
            buffer.putChar(0, this.spec.getCharacterFailure());
        }
        if (!spec.writeBuffer(channel, buffer, 2)) return null;
        if (!b1) {
            return null;
        }
        ((Buffer)buffer).clear();
        if (!spec.readBuffer(channel, buffer, 4)) return null;
        int length = buffer.getInt(0);
        ((Buffer)buffer).clear();
        ByteBuffer bufferM = ByteBuffer.allocate(length);
        if (!spec.readBuffer(channel, bufferM, length)) return null;
        String parcel = translateBuffer(bufferM);
        Parcel p = (Parcel) spec.decode(parcel, spec.getReverseMarkerFor(c));
        if (validator.apply(p)) {
            char c2 = spec.getMarkerFor(p.getClass());
            buffer.putChar(0, c2);
            if (!spec.writeBuffer(channel, buffer, 2)) return null;
            if (p instanceof Command) {
                this.spec.register(p.getId(), channel.socket());
            }
            return new Transaction(p, spec.getTransactionResultFor(c2));
        } else {
            buffer.putChar(0, this.spec.getCharacterFailure());
            if (!spec.writeBuffer(channel, buffer, 2)) return null;
            return new Transaction((Parcel) null, TransactionResult.VETOED);
        }
    }

    private String translateBuffer(ByteBuffer buffer) {
        ((Buffer)buffer).flip();
        return StandardCharsets.UTF_8.decode(buffer).toString();
    }

    private void register(Selector selector, ServerSocketChannel channel) throws IOException {
        SocketChannel cha = channel.accept();
        cha.configureBlocking(false);
        cha.setOption(StandardSocketOptions.TCP_NODELAY, true);
        cha.register(selector, SelectionKey.OP_READ);
    }
}
