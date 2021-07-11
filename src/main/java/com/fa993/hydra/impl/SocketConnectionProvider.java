package com.fa993.hydra.impl;

import com.fa993.hydra.core.Configuration;
import com.fa993.hydra.api.ConnectionProvider;
import com.fa993.hydra.api.ReceiverConnection;
import com.fa993.hydra.api.TransmitterConnection;

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
