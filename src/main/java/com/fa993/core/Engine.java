package com.fa993.core;

import com.fa993.api.ConnectionProvider;
import com.fa993.exceptions.*;
import com.fa993.misc.Utils;
import com.fasterxml.jackson.core.JsonParseException;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

public class Engine {

    private static Engine singleton;

    private Configuration configs;

    private ConnectionProvider provider;

    private State lastSeenState;

    private Server reordered;

    private int connectionTimeout;

    private int heartbeatDelay;

    private Instant lastActiveTime;

    private boolean stopReceiving;

    private boolean stopTransmitting;

    private Queue<WorkOrder> orders;

    /**
     * @param state - The initial state object {@link State}. As of now only the contents map is persisted and only if this server is the primary otherwise this object is ignored
     * @throws NoConfigurationFileException        If there is no configuration file provided
     * @throws MalformedConfigurationFileException If the configuration file is syntactically incorrect
     * @throws InvalidConnectionProviderException  If the ConnectionProvider is not matching the required specifications
     */
    public synchronized static void start(State state) throws NoConfigurationFileException, MalformedConfigurationFileException, InvalidConnectionProviderException {
        if (singleton == null) {
            singleton = new Engine(state);
            singleton.run();
        } else {
            throw new MultipleEngineException();
        }
    }

    /**
     * Note that this method guarantees at least one more pass through of the state object before it is taken out of the cluster
     *
     * @throws EngineNotStartedException if the engine has not started yet
     */
    public static void stop() {
        if (singleton != null) {
            singleton.stopReceiving = true;
        } else {
            throw new EngineNotStartedException();
        }
    }

    /**
     * @return the last seen {@link State} object
     * @throws EngineNotStartedException if the engine has not started yet
     */
    public static State state() {
        if (singleton != null) {
            return new State(singleton.lastSeenState);
        } else {
            throw new EngineNotStartedException();
        }
    }

    private Engine(State state) throws NoConfigurationFileException, MalformedConfigurationFileException, InvalidConnectionProviderException {
        this.configs = readConfigFile();
        try {
            this.provider = (ConnectionProvider) Class.forName(this.configs.getConnectionProvider()).getConstructor(Configuration.class).newInstance(this.configs);
        } catch (ReflectiveOperationException e) {
            throw new InvalidConnectionProviderException(e);
        }
        Server og = new Server(this.configs.getServers()[(this.configs.getCurrentServerIndex()) % this.configs.getServers().length]);
        this.reordered = og;
        for (int i = 1; i < this.configs.getServers().length; i++) {
            Server n = new Server(this.configs.getServers()[(i + this.configs.getCurrentServerIndex()) % this.configs.getServers().length]);
            this.reordered.setNext(n);
            this.reordered = n;
        }
        this.reordered.setNext(og);
        this.reordered = og;
        this.connectionTimeout = (this.configs.getCurrentServerIndex() + 1) * this.configs.getCooldownTime();
        this.stopTransmitting = false;
        this.stopReceiving = false;
        this.heartbeatDelay = this.configs.getHeartbeatTime();
        this.orders = new ConcurrentLinkedQueue<>();
        if (state != null) {
            this.lastSeenState = state.reissue(this.reordered.getServerURL());
        } else {
            this.lastSeenState = new State(this.reordered.getServerURL(), new HashMap<>());
        }
    }

    public Configuration readConfigFile() throws NoConfigurationFileException, MalformedConfigurationFileException {
        try {
            return Utils.obm.readValue(new File("src/main/resources/waterfall.json"), Configuration.class);
        } catch (JsonParseException e) {
            throw new MalformedConfigurationFileException(e);
        } catch (IOException e) {
            throw new NoConfigurationFileException(e);
        }
    }

    public void run() {
//        Thread t1 = new Thread(() -> {
//            while (!stopTransmitting || orders.size() > 0) {
//                WorkOrder order = this.orders.poll();
//                if (order == null) {
//                } else if (order.executeAfter.isAfter(Instant.now())) {
//                    this.orders.add(order);
//                } else {
//                    boolean found = false;
//                    Server n = this.reordered;
//                    String x = Stream.iterate(n, t -> t.getNext()).filter(t -> this.provider.getTransmitter().isUp(t.getServerURL())).findFirst().get().getServerURL();
////                    while (!this.provider.getTransmitter().isUp(n.getServerURl())) {
////                        n = n.getNext();
////                    }
////                    String x = n.getServerURl();
//                    if(x.equals(this.reordered.getServerURL())){
//
//                    } else {
//                        this.provider.getTransmitter().send(x, order.associatedState);
//                    }
//                }
//                try {
//                    Thread.sleep(1);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//        });
//        t1.setDaemon(true);
        Thread t2 = new Thread(() -> {
            while (!stopReceiving) {
                Optional<State> st = this.provider.getReceiver().receive(this.connectionTimeout);
                if (st.isPresent()) {
                    State newState = st.get();
                    if (this.lastSeenState.equals(newState)) {
                        //theOneTrueKingIsYouAgain()
                        this.lastSeenState = newState.reissue();
                        try {
                            Thread.sleep(this.heartbeatDelay);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        //theOneTrueKingIsNotYou()
                        this.lastSeenState = newState;
                    }
                    this.provider.getTransmitter().send(findFirstActiveServer(), this.lastSeenState);
                } else {
                    //theOneTrueKingIsYou();
                    if (!this.stopReceiving) {
                        this.lastSeenState = this.lastSeenState.reissue(this.reordered.getServerURL());
                        this.provider.getTransmitter().send(findFirstActiveServer(), this.lastSeenState);
                    }
                }
                this.lastActiveTime = Instant.now();
                stopTransmitting = stopReceiving;
            }
        });
        t2.setDaemon(true);
//        t1.start();
        t2.start();
    }

    private String findFirstActiveServer() {
        Server n = this.reordered.getNext();
        return Stream.iterate(n, t -> t.getNext()).filter(t -> this.provider.getTransmitter().isUp(t.getServerURL())).findFirst().get().getServerURL();
    }

}
