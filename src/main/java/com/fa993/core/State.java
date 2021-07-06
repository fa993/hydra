package com.fa993.core;

import com.fa993.misc.Utils;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class State {

    private String stateId;

    private String ownerURL;

    private Map<String, String> contents;

    public State() {
    }

    public State(String ownerURL, Map<String, String> contents) {
        this.stateId = Utils.newId();
        this.ownerURL = ownerURL;
        this.contents = contents;
    }

    public State(State state) {
        this.stateId = state.stateId;
        this.ownerURL = state.ownerURL;
        this.contents = state.contents;
    }

    public State reissue() {
        State ret = new State(this);
        ret.stateId = Utils.newId();
        return ret;
    }

    public State reissue(String ownerURL) {
        State ret = reissue();
        ret.ownerURL = ownerURL;
        return  ret;
    }

    public String getStateId() {
        return stateId;
    }

    public void setStateId(String stateId) {
        this.stateId = stateId;
    }

    public String getOwnerURL() {
        return ownerURL;
    }

    public void setOwnerURL(String ownerURL) {
        this.ownerURL = ownerURL;
    }

    public Map<String, String> getContents() {
        return contents;
    }

    public void setContents(Map<String, String> contents) {
        this.contents = contents;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        State state = (State) o;
        return Objects.equals(stateId, state.stateId) && Objects.equals(ownerURL, state.ownerURL) && Objects.equals(contents, state.contents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stateId, ownerURL, contents);
    }
}
