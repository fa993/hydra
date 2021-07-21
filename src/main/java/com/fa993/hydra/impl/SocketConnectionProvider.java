package com.fa993.hydra.impl;

import com.fa993.hydra.api.ConnectionProvider;
import com.fa993.hydra.api.ReceiverConnection;
import com.fa993.hydra.api.TransmitterConnection;
import com.fa993.hydra.core.Configuration;

public class SocketConnectionProvider implements ConnectionProvider {

    private Configuration configs;
    private TransmitterConnection transmitter;
    private ReceiverConnection receiver;

    public SocketConnectionProvider(Configuration configs) {
        this.configs = configs;
        ExchangeSpec spec = new ExchangeSpec();
        spec.init();
        this.transmitter = new SocketTransmitterConnection(this.configs.getServers()[this.configs.getCurrentServerIndex()], spec);
        this.receiver = new SocketReceiverConnection(this.configs.getServers()[this.configs.getCurrentServerIndex()], spec);
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
