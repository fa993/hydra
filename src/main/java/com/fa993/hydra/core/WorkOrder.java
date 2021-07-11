package com.fa993.hydra.core;

import java.util.HashMap;
import java.util.Map;

public class WorkOrder {

    private static Runnable DO_NOTHING = () -> {};

    public long executeAfter;
    public State associatedState;
    public Status status;
    private Map<Status, Runnable> ons;

    public WorkOrder() {
        this.ons = new HashMap<>();
    }

    public WorkOrder(long executeAfter, State associatedState, Status status) {
        this();
        this.executeAfter = executeAfter;
        this.associatedState = associatedState;
        this.status = status;
    }

    public WorkOrder on(Status s, Runnable r) {
        this.ons.put(s, r);
        return this;
    }

    public Runnable get(Status s){
        return this.ons.getOrDefault(s, DO_NOTHING);
    }
}
