package com.fa993.hydra.impl;

import com.fa993.hydra.core.Command;
import com.fa993.hydra.core.State;
import com.fa993.hydra.core.TransactionResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

public class ExchangeSpec {

    private ObjectMapper obm;

    private Map<Class, Character> markers;
    private Map<Character, Class> reverseMarkers;
    private Map<Character, TransactionResult> results;

    public ExchangeSpec() {
        this.obm = new ObjectMapper();
        this.markers = new HashMap<>();
        this.reverseMarkers = new HashMap<>();
        this.results = new HashMap<>();
    }

    public void init() {
        this.markers.put(State.class, 's');
        this.markers.put(Command.class, 'c');
        this.reverseMarkers.put('s', State.class);
        this.reverseMarkers.put('c', Command.class);
        this.results.put('s', TransactionResult.STATE);
        this.results.put('c', TransactionResult.COMMAND);
    }

    public String encode(Object o) throws Exception {
        return obm.writeValueAsString(o);
    }

    public Object decode(String s, Class c) throws Exception {
        return obm.readValue(s, c);
    }

    public Character getMarkerFor(Class c){
        return this.markers.get(c);
    }

    public Character getMarkerForOrDefault(Class c, Character def){
        return this.markers.getOrDefault(c, def);
    }

    public boolean containsMarker(Class c){
        return this.markers.containsKey(c);
    }

    public Class getReverseMarkerFor(Character c){
        return this.reverseMarkers.get(c);
    }

    public Class getReverseMarkerForOrDefault(Character c, Class cz){
        return this.reverseMarkers.getOrDefault(c, cz);
    }

    public boolean containsReverseMarker(Character c){
        return this.reverseMarkers.containsKey(c);
    }

    public TransactionResult getTransactionResultFor(Character c){
        return this.results.get(c);
    }

    public TransactionResult getTransactionResultForOrDefault(Character c, TransactionResult tr){
        return this.results.getOrDefault(c, tr);
    }

    public boolean containsTransactionResult(Character c){
        return this.results.containsKey(c);
    }
}
