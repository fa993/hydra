package com.fa993.misc;

import com.fa993.core.Engine;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

public class Utils {

    public static ObjectMapper obm = new ObjectMapper();

    public static String newId() {
        return UUID.randomUUID().toString();
    }
}
