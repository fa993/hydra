package com.fa993.exceptions;

public class MalformedConfigurationFileException extends Exception{

    public MalformedConfigurationFileException(Throwable cause) {
        super("Configuration File is not valid", cause);
    }
}
