package com.fa993.api;

import com.fa993.core.State;

public interface TransmitterConnection {

    public boolean send(String serverURL, State state);

}
