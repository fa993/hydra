package com.fa993.hydra.misc;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

public class Utils {

    public static ObjectMapper obm = new ObjectMapper();

    public static String newId() {
        return UUID.randomUUID().toString();
    }
}
