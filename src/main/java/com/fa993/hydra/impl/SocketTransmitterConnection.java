package com.fa993.hydra.impl;

import com.fa993.hydra.api.Parcel;
import com.fa993.hydra.api.TransmitterConnection;
import com.fa993.hydra.core.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;

public class SocketTransmitterConnection implements TransmitterConnection {

    private static final int MAX_WAIT_TIME = 200;

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
    public synchronized boolean send(String serverURL, Parcel parcel) {
        Socket operate = null;
        try {
            if (currentConnectedServerURL != null && currentConnectedServerURL.equals(serverURL)) {
                operate = currentSend;
            } else {
                operate = createConnection(serverURL);
            }
            boolean b = doSendMin(parcel, operate, this.centralBuffer);
            if (b) {
                currentSend = operate;
                currentConnectedServerURL = serverURL;
            }
            return b;
        } catch (Exception e) {
            try {
                if (operate != null) {
                    operate.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            return false;
        }
    }

    @Override
    public synchronized boolean send(String serverURL, Integer token) {
        Socket operate = null;
        try {
            if (currentConnectedServerURL != null && currentConnectedServerURL.equals(serverURL)) {
                operate = currentSend;
            } else {
                operate = createConnection(serverURL);
            }
            boolean b = doSendMin(token, operate, this.centralBuffer);
            if (b) {
                currentSend = operate;
                currentConnectedServerURL = serverURL;
            }
            return b;
        } catch (Exception e) {
            try {
                if (operate != null) {
                    operate.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            return false;
        }
    }

    @Override
    public synchronized boolean sendBack(Command c) {
        try (Socket returning = this.spec.consume(c.getId())) {
            return doSendMin(c, returning, this.secondaryBuffer);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean doSendMin(Parcel p, Socket socket, byte[] buffer) throws IOException {
        InputStream is = socket.getInputStream();
        OutputStream os = socket.getOutputStream();
        Byte m = spec.getByteMarkerFor(p.getClass());
        buffer[0] = m;
        if (p instanceof Command) {
            //do something
            //TODO
        }
        os.flush();
        return this.spec.getSuccessByte() == ((byte) is.read());
    }

    private boolean doSendMin(Integer i, Socket socket, byte[] buffer) throws IOException {
        InputStream is = socket.getInputStream();
        OutputStream os = socket.getOutputStream();
        Byte m = spec.getByteMarkerFor(Integer.class);
        buffer[0] = m;
        this.spec.serializeToken(i, buffer, 1);
        os.write(buffer, 0, 5);
        os.flush();
        return this.spec.getSuccessByte() == ((byte) is.read());
    }


    private Socket createConnection(String url) throws IOException {
        URL urlTo = new URL(url);
        Socket socket = new Socket(InetAddress.getByName(urlTo.getHost()), urlTo.getPort());
        socket.setTcpNoDelay(true);
        return socket;
    }
}
