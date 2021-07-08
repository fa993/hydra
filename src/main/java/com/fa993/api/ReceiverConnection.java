package com.fa993.api;

import com.fa993.core.State;

import java.util.Optional;
import java.util.function.Function;

public interface ReceiverConnection {

    /**
     * Impl Note: This method must block for at most the amount of time in timeout
     * Impl Note: This method must return an empty optional if the validator returns invalid(false)
     *
     * @param timeout   the time after which this method returns
     * @param validator the function which returns the validity of the received state, true if valid, false otherwise
     * @return An optional representing the state that was received or an empty optional if nothing was received
     */
    public Optional<State> receive(int timeout, Function<State, Boolean> validator);

}
