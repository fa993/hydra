package com.fa993.hydra.core;

import com.fa993.hydra.api.Parcel;
import com.fa993.hydra.exceptions.ObjectPoolExhaustedException;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Transaction {

    private static final LinkedBlockingQueue<Transaction> pool = new LinkedBlockingQueue<>();

    static void initialize(int poolSize) {
        for (int i = 0; i < poolSize; i++) {
            pool.add(new Transaction());
        }
    }

    public static Transaction getTransaction(int timeout) throws ObjectPoolExhaustedException {
        try {
            Transaction t0 = pool.poll(timeout, TimeUnit.MILLISECONDS);
            if (t0 != null) {
                return t0;
            } else throw new ObjectPoolExhaustedException();
        } catch (InterruptedException e) {
            //emergency.. should never happen
            Transaction t1 = new Transaction();
            pool.add(t1);
            return t1;
        }
    }

    static void reclaimTransaction(Transaction t) {
        t.clear();
        pool.add(t);
    }

    private String serverTo;
    private Parcel parcel;
    private TransactionResult result;

    private Transaction() {
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

    public void clear() {
        this.result = null;
        this.parcel = null;
    }
}
