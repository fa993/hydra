package com.fa993.hydra.core;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Configuration {

    @JsonProperty("servers")
    private String[] servers;

    @JsonProperty("current_server_index")
    private int currentServerIndex;

    @JsonProperty("heartbeat_time")
    private int heartbeatTime;

    @JsonProperty("cooldown_time")
    private int cooldownTime;

    @JsonProperty("connection_provider")
    private String connectionProvider;

    public Configuration() {
        this.servers = new String[0];
        this.currentServerIndex = 0;
        this.heartbeatTime = 1000;
        this.cooldownTime = 100000;
        this.connectionProvider = "com.fa993.hydra.impl.SocketConnectionProvider";
    }

    public int indexOf(String urlToFind){
        for(int i = 0; i < this.servers.length; i++){
            if(this.servers[i].equals(urlToFind)){
                return i;
            }
        }
        return -1;
    }

    public String[] getServers() {
        return servers;
    }

    public void setServers(String[] servers) {
        this.servers = servers;
    }

    public int getCurrentServerIndex() {
        return currentServerIndex;
    }

    public void setCurrentServerIndex(int currentServerIndex) {
        this.currentServerIndex = currentServerIndex;
    }

    public int getHeartbeatTime() {
        return heartbeatTime;
    }

    public void setHeartbeatTime(int heartbeatTime) {
        this.heartbeatTime = heartbeatTime;
    }

    public int getCooldownTime() {
        return cooldownTime;
    }

    public void setCooldownTime(int cooldownTime) {
        this.cooldownTime = cooldownTime;
    }

    public String getConnectionProvider() {
        return connectionProvider;
    }

    public void setConnectionProvider(String connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

}
