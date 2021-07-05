package com.fa993.core;

import java.time.Instant;

public class WorkOrder {

    public Instant executeAfter;
    public State associatedState;

    public WorkOrder() {
    }

    public WorkOrder(Instant executeAfter, State associatedState) {
        this.executeAfter = executeAfter;
        this.associatedState = associatedState;
    }
}
