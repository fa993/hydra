package com.fa993.hydra.exceptions;

public class ObjectPoolExhaustedException extends Exception {

    public ObjectPoolExhaustedException() {
        super("Object pool has been exhausted");
    }
}
