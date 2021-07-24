package com.fa993.hydra.api;

import com.fa993.hydra.core.Command;
import com.fa993.hydra.core.Token;

public interface ReceiverConnection {


    /**
     * Impl Note: This method must block for at most the amount of time in timeout
     *
     * @param timeout the maximum time after which this method must return
     * @return A {@link Token} object
     */
    public Token receiveToken(int timeout);

    /**
     * Impl Note: This method must block until a command is received
     *
     * @return A {@link Command} object
     */
    public Command receiveCommand();

}
