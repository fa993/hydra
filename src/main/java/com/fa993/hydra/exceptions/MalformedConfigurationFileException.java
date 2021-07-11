package com.fa993.hydra.exceptions;

public class MalformedConfigurationFileException extends Exception{

    public MalformedConfigurationFileException(Throwable cause) {
        super("Configuration File is not valid", cause);
    }
}
