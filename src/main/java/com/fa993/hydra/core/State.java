package com.fa993.hydra.core;

import com.fa993.hydra.api.Parcel;
import com.fa993.hydra.misc.Utils;

import java.util.Map;
import java.util.Objects;

public class State extends Parcel {

    private String ownerURL;

    private Map<String, String> contents;

    public State() {
        super(null);
    }

    public State(String ownerURL, Map<String, String> contents) {
        super(Utils.newId());
        this.ownerURL = ownerURL;
        this.contents = contents;
    }

    public State(State state) {
        super(state.id);
        this.ownerURL = state.ownerURL;
        this.contents = state.contents;
    }

    public State reissue() {
        State ret = new State(this);
        ret.id = Utils.newId();
        return ret;
    }

    public State reissue(String ownerURL) {
        State ret = reissue();
        ret.ownerURL = ownerURL;
        return ret;
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

    public String toLog() {
        return "[" + id + ", " + ownerURL + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        State state = (State) o;
        return Objects.equals(id, state.id) && Objects.equals(ownerURL, state.ownerURL) && Objects.equals(contents, state.contents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, ownerURL, contents);
    }
}
