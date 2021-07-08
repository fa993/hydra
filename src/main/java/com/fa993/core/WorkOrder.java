package com.fa993.core;

import java.time.Instant;

public class WorkOrder {

    public Instant executeAfter;
    public State associatedState;
    public Status status;

    public WorkOrder() {
    }

    public WorkOrder(Instant executeAfter, State associatedState, Status status) {
        this.executeAfter = executeAfter;
        this.associatedState = associatedState;
        this.status = status;
    }
}
