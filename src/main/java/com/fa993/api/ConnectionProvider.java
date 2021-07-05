package com.fa993.api;

public interface ConnectionProvider {

    public TransmitterConnection getTransmitter();

    public ReceiverConnection getReceiver();


}
