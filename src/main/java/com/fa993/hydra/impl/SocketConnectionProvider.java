package com.fa993.hydra.impl;

import com.fa993.hydra.core.Command;
import com.fa993.hydra.core.Configuration;
import com.fa993.hydra.api.ConnectionProvider;
import com.fa993.hydra.api.ReceiverConnection;
import com.fa993.hydra.api.TransmitterConnection;
import com.fa993.hydra.core.State;
import com.fa993.hydra.core.TransactionResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

public class SocketConnectionProvider implements ConnectionProvider {

    private Configuration configs;
    private SocketTransmitterConnection transmitter;
    private SocketReceiverConnection receiver;

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
