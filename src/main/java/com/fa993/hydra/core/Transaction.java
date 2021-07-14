package com.fa993.hydra.core;

import com.fa993.hydra.api.Parcel;

public class Transaction {

    private String serverTo;
    private Parcel parcel;
    private TransactionResult result;

    public Transaction() {
    }

    public Transaction(String serverTo, TransactionResult result) {
        this.serverTo = serverTo;
        this.result = result;
    }

    public Transaction(Parcel parcel, TransactionResult result) {
        this.parcel = parcel;
        this.result = result;
    }

    public String getServerTo() {
        return serverTo;
    }

    public void setServerTo(String serverTo) {
        this.serverTo = serverTo;
    }

    public Parcel getParcel() {
        return parcel;
    }

    public void setParcel(Parcel parcel) {
        this.parcel = parcel;
    }

    public TransactionResult getResult() {
        return result;
    }

    public void setResult(TransactionResult result) {
        this.result = result;
    }
}
