package com.fa993.hydra.exceptions;

public class InvalidConnectionProviderException extends Exception{

    public InvalidConnectionProviderException(Throwable cause) {
        super("Connection Provider is not valid", cause);
    }
}
