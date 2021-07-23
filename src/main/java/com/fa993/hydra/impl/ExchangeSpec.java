package com.fa993.hydra.impl;

import com.fa993.hydra.core.Command;
import com.fa993.hydra.core.State;
import com.fa993.hydra.core.Token;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Convenience class containing methods required by both the transmitter and the receiver. Also encapsulates co-ordination functionality between the receiver and the transmitter
 */
public class ExchangeSpec {

    private final ObjectMapper obm;

    private final Map<Class<?>, Character> markers;
    private final Map<Character, Class<?>> reverseMarkers;
    private final Map<Class<?>, Byte> byteMarkers;
    private final Map<Byte, Class<?>> reverseByteMarkers;
    private final Map<String, Socket> registeredCommands;
    private char success;
    private char failure;
    private byte successByte;
    private byte failureByte;

    public ExchangeSpec() {
        this.obm = new ObjectMapper();
        this.markers = new HashMap<>();
        this.reverseMarkers = new HashMap<>();
        this.byteMarkers = new HashMap<>();
        this.reverseByteMarkers = new HashMap<>();
        this.registeredCommands = new ConcurrentHashMap<>();
    }

    public void init() {
        this.markers.put(State.class, 's');
        this.markers.put(Command.class, 'c');
        this.reverseMarkers.put('s', State.class);
        this.reverseMarkers.put('c', Command.class);
        this.byteMarkers.put(Token.class, (byte) 1);
        this.byteMarkers.put(Command.class, (byte) 2);
        this.reverseByteMarkers.put((byte) 1, Token.class);
        this.reverseByteMarkers.put((byte) 2, Command.class);
        this.success = 'n';
        this.failure = 'f';
        this.successByte = 0;
        this.failureByte = 1;
    }

    public String encode(Object o) throws Exception {
        return obm.writeValueAsString(o);
    }

    public <T> T decode(String s, Class<T> c) throws Exception {
        return obm.readValue(s, c);
    }

    public Character getMarkerFor(Class c) {
        return this.markers.get(c);
    }

    public Character getMarkerForOrDefault(Class c, Character def) {
        return this.markers.getOrDefault(c, def);
    }

    public boolean containsMarker(Class c) {
        return this.markers.containsKey(c);
    }

    public Class getReverseMarkerFor(Character c) {
        return this.reverseMarkers.get(c);
    }

    public Class getReverseMarkerForOrDefault(Character c, Class cz) {
        return this.reverseMarkers.getOrDefault(c, cz);
    }

    public boolean containsReverseMarker(Character c) {
        return this.reverseMarkers.containsKey(c);
    }

    public Byte getByteMarkerFor(Class c) {
        return this.byteMarkers.get(c);
    }

    public Byte getByteMarkerForOrDefault(Class c, Byte def) {
        return this.byteMarkers.getOrDefault(c, def);
    }

    public boolean containsByteMarker(Class c) {
        return this.byteMarkers.containsKey(c);
    }

    public Class getReverseByteMarkerFor(Byte c) {
        return this.reverseByteMarkers.get(c);
    }

    public Class getReverseByteMarkerForOrDefault(Byte c, Class cz) {
        return this.reverseByteMarkers.getOrDefault(c, cz);
    }

    public boolean containsByteReverseMarker(Byte c) {
        return this.reverseByteMarkers.containsKey(c);
    }

    public void register(String key, Socket s) {
        this.registeredCommands.put(key, s);
    }

    public Socket consume(String key) {
        return this.registeredCommands.remove(key);
    }

    public boolean readBuffer(SocketChannel channel, ByteBuffer buffer, int readLength) throws IOException {
        int x = 0;
        int c = 0;
        ((Buffer) buffer).limit(readLength);
        while (x < readLength && c != -1) {
            c = channel.read(buffer);
            x += c;
        }
        return c != -1;
    }

    public boolean writeBuffer(SocketChannel channel, ByteBuffer buffer, int writeLength) throws IOException {
        int x = 0;
        int c = 0;
        ((Buffer) buffer).limit(writeLength);
        while (x < writeLength && c != -1) {
            c = channel.write(buffer);
            x += c;
        }
        return c != -1;
    }

    public boolean readBuffer(InputStream is, byte[] buffer, int offset, int readLength) throws IOException {
        int x = 0;
        int c = 0;
        while (x < readLength && c != -1) {
            c = is.read(buffer, offset + x, readLength - x);
            x += c;
        }
        return c != -1;
    }

    public char getCharacterSuccess() {
        return this.success;
    }

    public char getCharacterFailure() {
        return this.failure;
    }

    public byte getSuccessByte() {
        return successByte;
    }

    public byte getFailureByte() {
        return failureByte;
    }

    public void serializeToken(Token t, byte[] buffer, int offset) {
        buffer[offset + 0] = (byte) ((t.getTokenIssuerIndex() >> 24) & 0xFF);
        buffer[offset + 1] = (byte) ((t.getTokenIssuerIndex() >> 16) & 0xFF);
        buffer[offset + 2] = (byte) ((t.getTokenIssuerIndex() >> 8) & 0xFF);
        buffer[offset + 3] = (byte) ((t.getTokenIssuerIndex() >> 0) & 0xFF);
        buffer[offset + 4] = (byte) ((t.getTokenId() >> 56) & 0xFF);
        buffer[offset + 5] = (byte) ((t.getTokenId() >> 48) & 0xFF);
        buffer[offset + 6] = (byte) ((t.getTokenId() >> 40) & 0xFF);
        buffer[offset + 7] = (byte) ((t.getTokenId() >> 32) & 0xFF);
        buffer[offset + 8] = (byte) ((t.getTokenId() >> 24) & 0xFF);
        buffer[offset + 9] = (byte) ((t.getTokenId() >> 16) & 0xFF);
        buffer[offset + 10] = (byte) ((t.getTokenId() >> 8) & 0xFF);
        buffer[offset + 11] = (byte) ((t.getTokenId() >> 0) & 0xFF);
    }

    public void deserializeToken(byte[] buffer, int offset, Token t1) {
        t1.setTokenId(byteArrayToLong(buffer, offset + 4));
        t1.setTokenIssuerIndex(byteArrayToInt(buffer, offset + 0));
    }

    public int byteArrayToInt(byte[] buffer, int start) {
        return
                ((buffer[start + 0] & 0xFF) << 24) |
                        ((buffer[start + 1] & 0xFF) << 16) |
                        ((buffer[start + 2] & 0xFF) << 8) |
                        ((buffer[start + 3] & 0xFF) << 0);
    }

    public long byteArrayToLong(byte[] buffer, int start) {
        return
                ((long) (buffer[start + 0] & 0xFF) << 56) |
                        ((long) (buffer[start + 1] & 0xFF) << 48) |
                        ((long) (buffer[start + 2] & 0xFF) << 40) |
                        ((long) (buffer[start + 3] & 0xFF) << 32) |
                        ((long) (buffer[start + 4] & 0xFF) << 24) |
                        ((long) (buffer[start + 5] & 0xFF) << 16) |
                        ((long) (buffer[start + 6] & 0xFF) << 8) |
                        ((long) (buffer[start + 7] & 0xFF) << 0);
    }
}
