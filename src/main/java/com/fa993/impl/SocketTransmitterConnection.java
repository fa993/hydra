package com.fa993.impl;

import com.fa993.api.TransmitterConnection;
import com.fa993.core.State;
import com.fa993.misc.Utils;

import java.io.*;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;

//TODO idk if this class even works
public class SocketTransmitterConnection implements TransmitterConnection {

    private String myServer;

    public SocketTransmitterConnection(String myServerURL) {
        this.myServer = myServerURL;
    }

    @Override
    public boolean send(String serverURL, State state) {
        if (serverURL.equals(myServer)) {
            return true;
        }
        try {
            URL urlTo = new URL(serverURL);
            Socket socket = new Socket(InetAddress.getByName(urlTo.getHost()), urlTo.getPort());
            socket.setTcpNoDelay(true);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            writer.write(Utils.obm.writeValueAsString(state) + "\n");
            writer.flush();
            char c = (char) new BufferedReader(new InputStreamReader(socket.getInputStream())).read();
            socket.close();
            return c == '0';
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
