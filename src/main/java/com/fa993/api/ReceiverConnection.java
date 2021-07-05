package com.fa993.api;

import com.fa993.core.State;

import java.util.Optional;

public interface ReceiverConnection {

    //timeout in millis, should be blocking
    public Optional<State> receive(int timeout);

}
