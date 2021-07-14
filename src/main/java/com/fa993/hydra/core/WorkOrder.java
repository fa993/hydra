package com.fa993.hydra.core;

import com.fa993.hydra.api.Parcel;

import java.util.HashMap;
import java.util.Map;

public class WorkOrder {

    private static final Runnable DO_NOTHING = () -> {
    };

    public long executeAfter;
    public Parcel associatedParcel;
    public Status status;
    private Map<Status, Runnable> ons;

    public WorkOrder() {
        this.ons = new HashMap<>();
    }

    public WorkOrder(long executeAfter, Parcel associatedParcel, Status status) {
        this();
        this.executeAfter = executeAfter;
        this.associatedParcel = associatedParcel;
        this.status = status;
    }

    public WorkOrder on(Status s, Runnable r) {
        this.ons.put(s, r);
        return this;
    }

    public Runnable get(Status s) {
        return this.ons.getOrDefault(s, DO_NOTHING);
    }
}
