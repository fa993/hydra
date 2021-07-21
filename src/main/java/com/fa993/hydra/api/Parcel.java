package com.fa993.hydra.api;

public abstract class Parcel {

    protected String id;

    public Parcel(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
