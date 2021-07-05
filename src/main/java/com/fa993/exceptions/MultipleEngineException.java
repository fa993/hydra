package com.fa993.exceptions;

public class MultipleEngineException extends RuntimeException{

    public MultipleEngineException() {
        super("Cannot start new engine as one is already started");
    }
}
