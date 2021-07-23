package com.fa993.hydra.core;

import com.fa993.hydra.api.Parcel;
import com.fa993.hydra.exceptions.ObjectPoolExhaustedException;
import com.fa993.hydra.misc.Utils;

import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Token implements Parcel {

    private static final LinkedBlockingQueue<Token> pool = new LinkedBlockingQueue<>();

    static void initialize(int poolSize) {
        for (int i = 0; i < poolSize; i++) {
            pool.add(new Token());
        }
    }

    public static Token getToken(int timeout) throws ObjectPoolExhaustedException {
        try {
            Token t0 = pool.poll(timeout, TimeUnit.MILLISECONDS);
            if (t0 != null) {
                return t0;
            } else throw new ObjectPoolExhaustedException();
        } catch (InterruptedException ex) {
            //emergency... should never happen
            Token t1 = new Token();
            pool.add(t1);
            return t1;
        }
    }

    static void reclaimToken(Token t) {
        pool.add(t);
    }

    private long tokenId;
    private int tokenIssuerIndex;

    private Token() {
    }

    public void reissue() {
        this.tokenId = Utils.newLongId();
    }

    public long getTokenId() {
        return tokenId;
    }

    public void setTokenId(long tokenId) {
        this.tokenId = tokenId;
    }

    public int getTokenIssuerIndex() {
        return tokenIssuerIndex;
    }

    public void setTokenIssuerIndex(int tokenIssuerIndex) {
        this.tokenIssuerIndex = tokenIssuerIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Token token = (Token) o;
        return tokenId == token.tokenId && tokenIssuerIndex == token.tokenIssuerIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tokenId, tokenIssuerIndex);
    }

    public String toLog() {
        return "[" + this.tokenId + ", " + this.tokenIssuerIndex + "]";
    }
}
