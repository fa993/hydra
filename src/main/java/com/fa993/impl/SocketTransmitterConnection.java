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

    private URL myServerURL;
    private String myServer;

    public SocketTransmitterConnection(String myServerURL) {
        try {
            this.myServer = myServerURL;
            this.myServerURL = new URL(myServerURL);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean send(String serverURL, State state) {
        if (serverURL.equals(myServer)) {
            return true;
        }
        try {
            URL urlTo = new URL(serverURL);
            Socket socket = new Socket(this.myServerURL.getHost(), this.myServerURL.getPort(), InetAddress.getByName(urlTo.getHost()), urlTo.getPort());
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            writer.write(Utils.obm.writeValueAsString(state));
            writer.flush();
            writer.close();
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            boolean bl = reader.readLine().charAt(0) == '0';
            socket.close();
            return bl;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean isUp(String serverURL) {
        if (serverURL.equals(myServer)) {
            return true;
        }
        try {
            URL urlTo = new URL(serverURL);
            Socket socket = new Socket(this.myServerURL.getHost(), this.myServerURL.getPort(), InetAddress.getByName(urlTo.getHost()), urlTo.getPort());
            socket.getOutputStream().close();
            InputStreamReader isr = new InputStreamReader(socket.getInputStream());
            boolean bl = isr.read() == 0;
            isr.close();
            socket.close();
            return bl;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
