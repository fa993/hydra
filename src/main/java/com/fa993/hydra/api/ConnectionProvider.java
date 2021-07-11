package com.fa993.hydra.api;

public interface ConnectionProvider {

    public TransmitterConnection getTransmitter();

    public ReceiverConnection getReceiver();


}
