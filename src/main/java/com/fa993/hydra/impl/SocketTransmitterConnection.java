package com.fa993.hydra.impl;

import com.fa993.hydra.api.Parcel;
import com.fa993.hydra.api.TransmitterConnection;
import com.fa993.hydra.core.Command;
import com.fa993.hydra.core.Token;
import com.fa993.hydra.core.TransactionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class SocketTransmitterConnection implements TransmitterConnection {

    private static final Logger logger = LoggerFactory.getLogger(SocketTransmitterConnection.class);

    private final String myServer;
    private final ExchangeSpec spec;

    private String currentConnectedServerURL;
    private Socket currentSend;

    private final byte[] centralBuffer;
    private final byte[] secondaryBuffer;

    public SocketTransmitterConnection(String myServerURL, ExchangeSpec spec) {
        this.myServer = myServerURL;
        this.spec = spec;
        this.centralBuffer = new byte[4096];
        this.secondaryBuffer = new byte[4096];
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
            TransactionResult tr = doSendMin(parcel, operate, this.centralBuffer);
            if (tr != TransactionResult.FAILURE) {
                currentSend = operate;
                currentConnectedServerURL = serverURL;
            }
            return tr;
        } catch (Exception e) {
            return TransactionResult.FAILURE;
        }
    }

    @Override
    public synchronized TransactionResult sendBack(Command c) {
        Socket returning = this.spec.consume(c.getId());
        try {
            return doSendMin(c, returning, this.secondaryBuffer);
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
        int i = reader.read();
        if (i == -1) {
            socket.close();
            return TransactionResult.FAILURE;
        }
        char res = (char) i;
        if (res != this.spec.getCharacterSuccess()) {
            socket.close();
            logger.error("Response indicates that the server isn't ready to accept object");
            return TransactionResult.FAILURE;
        }
        String encoded = spec.encode(parcel);
        writer.write(encoded + "\n");
        writer.flush();
        char c = (char) reader.read();
        return spec.containsReverseMarker(c) ? TransactionResult.SUCCESS : TransactionResult.VETOED;
    }

    private TransactionResult doSendMin(Parcel p, Socket socket, byte[] buffer) throws IOException {
        InputStream is = socket.getInputStream();
        OutputStream os = socket.getOutputStream();
        Byte m = spec.getByteMarkerFor(p.getClass());
        buffer[0] = m;
        if (p instanceof Token) {
            this.spec.serializeToken((Token) p, buffer, 1);
            os.write(buffer, 0, 13);
        } else if (p instanceof Command) {
            //do something
            //TODO
        }
        os.flush();
        return this.spec.getSuccessByte() == ((byte) is.read()) ? TransactionResult.SUCCESS : TransactionResult.VETOED;
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
        return spec.containsReverseMarker(buffer.getChar(0)) ? TransactionResult.SUCCESS : TransactionResult.VETOED;
    }

    private Socket createConnection(String url) throws IOException {
        URL urlTo = new URL(url);
        Socket socket = new Socket(InetAddress.getByName(urlTo.getHost()), urlTo.getPort());
        socket.setTcpNoDelay(true);
        return socket;
    }
}
