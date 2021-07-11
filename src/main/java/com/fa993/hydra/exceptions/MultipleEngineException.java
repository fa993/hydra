package com.fa993.hydra.exceptions;

public class MultipleEngineException extends RuntimeException{

    public MultipleEngineException() {
        super("Cannot start new engine as one is already started");
    }
}
