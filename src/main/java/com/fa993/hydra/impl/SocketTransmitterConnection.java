package com.fa993.hydra.impl;

import com.fa993.hydra.api.Parcel;
import com.fa993.hydra.api.TransmitterConnection;
import com.fa993.hydra.core.Command;
import com.fa993.hydra.core.TransactionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.sql.Struct;
import java.util.HashMap;
import java.util.Map;

public class SocketTransmitterConnection implements TransmitterConnection {

    private static Logger logger = LoggerFactory.getLogger(SocketTransmitterConnection.class);

    private String myServer;
    private ExchangeSpec spec;

    private String currentConnectedServerURL;
    private Socket currentSend;

    public SocketTransmitterConnection(String myServerURL, ExchangeSpec spec) {
        this.myServer = myServerURL;
        this.spec = spec;
    }

    @Override
    public synchronized TransactionResult send(String serverURL, Parcel parcel) {
        Socket operate;
        try {
            if (currentConnectedServerURL != null && currentConnectedServerURL.equals(serverURL)) {
                operate = currentSend;
            } else {
                operate = createConnection(serverURL);
            }
            TransactionResult tr = doSend(parcel, operate);
            currentSend = operate;
            currentConnectedServerURL = serverURL;
            return tr;
        } catch (Exception e) {
            return TransactionResult.FAILURE;
        }
    }

    @Override
    public synchronized TransactionResult sendBack(Command c) {
        Socket returning = this.spec.consume(c.getId());
        try {
            return doSend(c, returning.getChannel());
        } catch (Exception e) {
            try {
                returning.close();
            } catch (Exception ex) {

            }
            return TransactionResult.FAILURE;
        }
    }

//    private TransactionResult doSendBack(Command c, Socket s) throws Exception {
//        SocketChannel channel = s.getChannel();
//        ByteBuffer buffer = ByteBuffer.allocate(4);
//        buffer.putChar(spec.getMarkerFor(c.getClass()));
//        this.spec.writeBuffer(channel, buffer, 2);
//        ((Buffer)buffer).clear();
//        this.spec.readBuffer(channel, buffer, 2);
//        char res = buffer.getChar();
//        if (res != '0') {
//            channel.close();
//            logger.error("Response indicates that the server isn't ready to accept object");
//            return TransactionResult.FAILURE;
//        }
//        String encoded = this.spec.encode(c);
//        ByteBuffer bufc = StandardCharsets.UTF_8.encode(encoded);
//        ((Buffer)buffer).clear();
//        int cap = bufc.capacity();
//        buffer.putInt(cap);
//        this.spec.writeBuffer(channel, buffer, 4);
//        this.spec.writeBuffer(channel, bufc, cap);
//        ((Buffer)buffer).clear();
//        this.spec.readBuffer(channel, buffer, 2);
//        return spec.getTransactionResultForOrDefault(buffer.getChar(), TransactionResult.VETOED);
//    }

    private TransactionResult doSend(Parcel parcel, Socket socket) throws Exception {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer.write(spec.getMarkerFor(parcel.getClass()));
        writer.flush();
        char res = (char) reader.read();
        if (res != this.spec.getCharacterSuccess()) {
            socket.close();
            logger.error("Response indicates that the server isn't ready to accept object");
            return TransactionResult.FAILURE;
        }
        String encoded = spec.encode(parcel);
        writer.write(encoded + "\n");
        writer.flush();
        char c = (char) reader.read();
        return spec.getTransactionResultForOrDefault(c, TransactionResult.VETOED);
    }


    private TransactionResult doSend(Parcel parcel, SocketChannel socketChannel) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putChar(0, spec.getMarkerFor(parcel.getClass()));
        if (!this.spec.writeBuffer(socketChannel, buffer, 2)) throw new Exception();
        ((Buffer) buffer).clear();
        if (!this.spec.readBuffer(socketChannel, buffer, 2)) throw new Exception();
        char res = buffer.getChar(0);
        if (res != this.spec.getCharacterSuccess()) {
            socketChannel.close();
            logger.error("Response indicates that the server isn't ready to accept object");
            return TransactionResult.FAILURE;
        }
        String encoded = this.spec.encode(parcel);
        ByteBuffer bufc = StandardCharsets.UTF_8.encode(encoded);
        ((Buffer) buffer).clear();
        int cap = bufc.limit();
        buffer.putInt(0, cap);
        if (!this.spec.writeBuffer(socketChannel, buffer, 4)) throw new Exception();
        if (!this.spec.writeBuffer(socketChannel, bufc, cap)) throw new Exception();
        ((Buffer) buffer).clear();
        if (!this.spec.readBuffer(socketChannel, buffer, 2)) throw new Exception();
        return spec.getTransactionResultForOrDefault(buffer.getChar(0), TransactionResult.VETOED);
    }

    private Socket createConnection(String url) throws IOException {
        URL urlTo = new URL(url);
        Socket socket = new Socket(InetAddress.getByName(urlTo.getHost()), urlTo.getPort());
        socket.setTcpNoDelay(true);
        return socket;
    }
}
