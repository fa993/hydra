package com.fa993.hydra.core;

public class Server {

    private String serverURL;
    private Server next;

    public Server(String serverURL) {
        this.serverURL = serverURL;
        this.next = null;
    }

    public Server(String serverURL, Server next) {
        this.serverURL = serverURL;
        this.next = next;
    }

    public String getServerURL() {
        return serverURL;
    }

    public void setServerURL(String serverURL) {
        this.serverURL = serverURL;
    }

    public Server getNext() {
        return next;
    }

    public void setNext(Server next) {
        this.next = next;
    }
}
