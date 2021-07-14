package com.fa993.hydra.core;

import com.fa993.hydra.api.Parcel;

import java.util.HashMap;
import java.util.Map;

public class Command implements Parcel {

    private Map<String, String> headers;

    public Command() {
        this.headers = new HashMap<>();
    }


}
