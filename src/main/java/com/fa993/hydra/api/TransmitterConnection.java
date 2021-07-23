package com.fa993.hydra.api;

import com.fa993.hydra.core.Command;
import com.fa993.hydra.core.State;
import com.fa993.hydra.core.TransactionResult;
import com.fa993.hydra.exceptions.ObjectPoolExhaustedException;

public interface TransmitterConnection {

    /**
     * @param serverURL The server to be sent to
     * @param parcel    the parcel that must be sent
     * @return the {@link TransactionResult} which represents the result of this operation
     */
    public TransactionResult send(String serverURL, Parcel parcel);

    /**
     * @param c the command that has to be sent back to the external source with created it
     * @return the {@link TransactionResult} which represents the result of this operation
     */
    public TransactionResult sendBack(Command c);

}
