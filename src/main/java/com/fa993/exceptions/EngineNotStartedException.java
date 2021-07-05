package com.fa993.exceptions;

public class EngineNotStartedException extends RuntimeException{

    public EngineNotStartedException() {
        super("Cannot get state because engine has not started");
    }
}
