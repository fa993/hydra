package com.fa993.hydra.core;

import com.fa993.hydra.api.Parcel;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Command implements Parcel {

    private Map<String, String> headers;

    public Command() {
        this.headers = new HashMap<>();
    }

    public String getValue(String header){
        return this.headers.get(header);
    }

    public void appendValue(String header, String value) {
        this.headers.put(header, this.headers.getOrDefault(header, "") + value + ", ");
    }

    public void setValue(String header, String value){
        this.headers.put(header, value);
    }

    public Set<String> getAllHeaders() {
        return this.headers.keySet();
    }

}
