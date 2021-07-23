package com.fa993.hydra.api;

import com.fa993.hydra.core.Token;
import com.fa993.hydra.core.Transaction;
import com.fa993.hydra.core.TransactionResult;
import com.fa993.hydra.exceptions.ObjectPoolExhaustedException;

import java.util.function.Function;

public interface ReceiverConnection {


    /**
     * @param validator
     */
    public void configureValidatorForState(Function<Token, Boolean> validator);

    /**
     * Impl Note: This method must block for at most the amount of time in timeout
     * Impl Note: The returned transaction object must have a non-null {@link TransactionResult} field and for a successful transaction have a non-null {@link Parcel}
     *
     * @param timeout the maximum time after which this method must return
     * @return A {@link Transaction} object that represents the details of this transaction.
     */
    public Transaction receiveToken(int timeout) throws ObjectPoolExhaustedException;

    public Transaction receiveCommand() throws ObjectPoolExhaustedException;

}
