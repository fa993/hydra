package com.fa993.testing;

import com.fa993.core.Engine;
import com.fa993.exceptions.InvalidConnectionProviderException;
import com.fa993.exceptions.MalformedConfigurationFileException;
import com.fa993.exceptions.NoConfigurationFileException;

public class TestClass {

    public static void main(String[] args) throws InvalidConnectionProviderException, NoConfigurationFileException, MalformedConfigurationFileException, InterruptedException {
        Engine.start(null);
        int i = 0;
        while (true){
            i++;
            Thread.sleep(1000);
        }
    }

}
