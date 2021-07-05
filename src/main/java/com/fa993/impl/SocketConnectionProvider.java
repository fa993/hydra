package com.fa993.impl;

import com.fa993.api.ConnectionProvider;
import com.fa993.api.ReceiverConnection;
import com.fa993.api.TransmitterConnection;
import com.fa993.core.Configuration;

public class SocketConnectionProvider implements ConnectionProvider {

    private Configuration configs;
    private SocketTransmitterConnection transmitter;
    private SocketReceiverConnection receiver;

    public SocketConnectionProvider(Configuration configs) {
        this.configs = configs;
        this.transmitter = new SocketTransmitterConnection(this.configs.getServers()[this.configs.getCurrentServerIndex()]);
        this.receiver = new SocketReceiverConnection(this.configs.getServers()[this.configs.getCurrentServerIndex()]);
    }

    @Override
    public TransmitterConnection getTransmitter() {
        return this.transmitter;
    }

    @Override
    public ReceiverConnection getReceiver() {
        return this.receiver;
    }
}
