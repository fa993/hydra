package com.fa993.exceptions;

public class EngineNotInitializedException extends RuntimeException{

    public EngineNotInitializedException() {
        super("Cannot do operation because engine has not been initialized");
    }
}
