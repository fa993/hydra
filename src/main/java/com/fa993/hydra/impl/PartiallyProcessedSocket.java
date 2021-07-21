package com.fa993.hydra.impl;

import java.net.Socket;
import java.util.Objects;

public class PartiallyProcessedSocket {

    private Socket s;
    private int firstByte;

    public PartiallyProcessedSocket(Socket s, int firstByte) {
        this.s = s;
        this.firstByte = firstByte;
    }

    public Socket getS() {
        return s;
    }

    public void setS(Socket s) {
        this.s = s;
    }

    public int getFirstByte() {
        return firstByte;
    }

    public void setFirstByte(int firstByte) {
        this.firstByte = firstByte;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PartiallyProcessedSocket that = (PartiallyProcessedSocket) o;
        return firstByte == that.firstByte && Objects.equals(s, that.s);
    }

    @Override
    public int hashCode() {
        return Objects.hash(s, firstByte);
    }
}
