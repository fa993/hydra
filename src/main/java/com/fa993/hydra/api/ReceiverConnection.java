package com.fa993.hydra.api;

import com.fa993.hydra.core.Command;

public interface ReceiverConnection {


    /**
     * Impl Note: This method must block for at most the amount of time in timeout
     *
     * @param timeout the maximum time after which this method must return
     * @return an integer object conforming to the index of this server in the config file
     */
    public Integer receiveToken(int timeout);

    /**
     * Impl Note: This method must block until a command is received
     *
     * @return A {@link Command} object
     */
    public Command receiveCommand();

}
