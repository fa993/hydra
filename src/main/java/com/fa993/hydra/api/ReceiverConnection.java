package com.fa993.hydra.api;

import com.fa993.hydra.core.State;
import com.fa993.hydra.core.TransactionResult;
import com.fa993.hydra.core.Transaction;

import java.util.function.Function;

public interface ReceiverConnection {

    /**
     * Impl Note: This method must block for at most the amount of time in timeout
     * Impl Note: This method must return an empty optional if the validator returns invalid(false)
     * Impl Note: The returned transaction object must have a non-null {@link TransactionResult} field and for a successful transaction have a non-null {@link Parcel}
     *
     * @param timeout   the time after which this method returns
     * @param validator the function which returns the validity of the received parcel, true if valid, false otherwise
     * @return A {@link Transaction} object that represents the details of this transaction.
     */
    public Transaction receive(int timeout, Function<Parcel, Boolean> validator);

}
