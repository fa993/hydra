package com.fa993.core;

public class Transaction {

    private String serverTo;
    private TransactionResult result;

    public Transaction() {
    }

    public Transaction(String serverTo, TransactionResult result) {
        this.serverTo = serverTo;
        this.result = result;
    }

    public String getServerTo() {
        return serverTo;
    }

    public void setServerTo(String serverTo) {
        this.serverTo = serverTo;
    }

    public TransactionResult getResult() {
        return result;
    }

    public void setResult(TransactionResult result) {
        this.result = result;
    }
}
