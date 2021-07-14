package com.fa993.hydra.impl;

import com.fa993.hydra.api.Parcel;
import com.fa993.hydra.api.TransmitterConnection;
import com.fa993.hydra.core.TransactionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;

public class SocketTransmitterConnection implements TransmitterConnection {

    private static Logger logger = LoggerFactory.getLogger(SocketTransmitterConnection.class);

    private String myServer;
    private ExchangeSpec spec;

    public SocketTransmitterConnection(String myServerURL, ExchangeSpec spec) {
        this.myServer = myServerURL;
        this.spec = spec;
    }

    @Override
    public TransactionResult send(String serverURL, Parcel parcel) {
        try {
            URL urlTo = new URL(serverURL);
            Socket socket = new Socket(InetAddress.getByName(urlTo.getHost()), urlTo.getPort());
            socket.setTcpNoDelay(true);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer.write(spec.getMarkerFor(parcel.getClass()));
            writer.flush();
            char res = (char) reader.read();
            if (res != '0') {
                socket.close();
                logger.error("Response indicates that the server isn't ready to accept object");
                return TransactionResult.FAILURE;
            }
            writer.write(spec.encode(parcel) + "\n");
            writer.flush();
            char c = (char) reader.read();
            socket.close();
            return spec.getTransactionResultForOrDefault(c, TransactionResult.VETOED);
        } catch (Exception e) {
            return TransactionResult.FAILURE;
        }
    }
}
