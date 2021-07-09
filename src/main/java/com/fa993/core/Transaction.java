package com.fa993.core;

public class Transaction {

    private String serverTo;
    private State state;
    private TransactionResult result;

    public Transaction() {
    }

    public Transaction(String serverTo, TransactionResult result) {
        this.serverTo = serverTo;
        this.result = result;
    }

    public Transaction(State state, TransactionResult result) {
        this.state = state;
        this.result = result;
    }

    public String getServerTo() {
        return serverTo;
    }

    public void setServerTo(String serverTo) {
        this.serverTo = serverTo;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public TransactionResult getResult() {
        return result;
    }

    public void setResult(TransactionResult result) {
        this.result = result;
    }
}
