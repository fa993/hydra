package com.fa993.hydra.api;

import com.fa993.hydra.core.Command;

public interface TransmitterConnection {

    /**
     * @param serverURL The server to be sent to
     * @param token     the integer representing the index of this server in the configuration file
     * @return true if the send is successful, false otherwise
     */
    public boolean send(String serverURL, Integer token);

    /**
     * @param serverURL The server to be sent to
     * @param parcel    the parcel that must be sent
     * @return true if the send is successful, false otherwise
     */
    public boolean send(String serverURL, Parcel parcel);

    /**
     * @param c the command that has to be sent back to the external source with created it
     * @return true if the send is successful, false otherwise
     */
    public boolean sendBack(Command c);

}
