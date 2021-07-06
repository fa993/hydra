package com.fa993.core;

import com.fa993.api.ConnectionProvider;
import com.fa993.exceptions.*;
import com.fa993.misc.Utils;
import com.fasterxml.jackson.core.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class Engine {

    private static Logger logger = LoggerFactory.getLogger(Engine.class);

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

    private Consumer<Map<String, String>> callback;

    private Map<String, String> contents;

    private ExecutorService executor = Executors.newSingleThreadExecutor((r) -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    /**
     * Makes this server ready for insertion into the cluster
     *
     * @param contents The initial contents object. Will only be persisted if this server is primary on start up
     * @param callback the callback which will be run when this server becomes primary
     * @throws NoConfigurationFileException        If there is no configuration file provided
     * @throws MalformedConfigurationFileException If the configuration file is syntactically incorrect
     * @throws InvalidConnectionProviderException  If the ConnectionProvider is not matching the required specifications
     */
    public synchronized static void start(Map<String, String> contents, Consumer<Map<String, String>> callback) throws NoConfigurationFileException, MalformedConfigurationFileException, InvalidConnectionProviderException {
        if (singleton == null) {
            singleton = new Engine(contents, callback);
            singleton.run();
        } else {
            throw new MultipleEngineException();
        }
    }

    /**
     * Prepares this server for removal from this cluster
     * Note that this method guarantees at least one more pass through of the state object through this cluster before this server is taken out of the cluster
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
     * Get the most recent contents object
     *
     * @return the contents object which may or may not have originated from this server
     * @throws EngineNotStartedException if the engine has not started yet
     */
    public static Map<String, String> contents() {
        if (singleton != null) {
            return singleton.lastSeenState.getContents();
        } else {
            throw new EngineNotStartedException();
        }
    }

    /**
     * Check if this server is primary
     *
     * @return true if this server is the primary, false otherwise
     * @throws EngineNotStartedException if the engine has not started yet
     */
    public static boolean isPrimary() {
        if (singleton != null) {
            return singleton.lastSeenState.getOwnerURL().equals(singleton.reordered.getServerURL());
        } else {
            throw new EngineNotStartedException();
        }
    }

    /**
     * Only use this method if the callback may change dynamically, otherwise use the parameter in the constructor
     *
     * @param callback The new callback when this server becomes primary
     * @throws EngineNotStartedException if the engine has not started yet
     */
    public static void onPrimary(Consumer<Map<String, String>> callback) {
        if (singleton != null) {
            singleton.callback = callback;
        } else {
            throw new EngineNotStartedException();
        }
    }

    /**
     * Use this method to update the contents object to better reflect the state of this server
     *
     * @param contents the contents which will be passed to other servers if this server is primary
     */
    public static void pushContents(Map<String, String> contents) {
        if (singleton != null) {
            singleton.contents = contents;
        } else {
            throw new EngineNotStartedException();
        }
    }

    private Engine(Map<String, String> contents, Consumer<Map<String, String>> callback) throws NoConfigurationFileException, MalformedConfigurationFileException, InvalidConnectionProviderException {
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
        this.contents = contents;
        if (callback != null) {
            this.callback = callback;
        } else {
            this.callback = t -> {
            };
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
//                    while (!this.provider.getTransmitter().isUp(n.getServerURl())) {
//                        n = n.getNext();
//                    }
//                    String x = n.getServerURl();
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
                    logger.debug("Received state: " + newState.toLog());
                    if (newState.equals(this.lastSeenState)) {
                        //theOneTrueKingIsYouAgain()
                        this.lastSeenState = newState.reissue();
                        this.lastSeenState.setContents(this.contents);
                        logger.debug("Retained Primary: " + this.lastSeenState.toLog());
                        try {
                            Thread.sleep(this.heartbeatDelay);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        //theOneTrueKingIsNotYou()
                        this.lastSeenState = newState;
                        logger.debug("Not Primary");
                    }
                    String serverTo = sendToFirstActiveServer(this.lastSeenState);
                    logger.debug("Sent state to " + serverTo);
                } else {
                    //theOneTrueKingIsYou();
                    if (!this.stopReceiving) {
                        this.lastSeenState = new State(this.reordered.getServerURL(), this.contents);
                        logger.debug("Became Primary due to timeout: " + this.lastSeenState.toLog());
                        String serverTo = sendToFirstActiveServer(this.lastSeenState);
                        logger.debug("Sent state to " + serverTo);
                        executor.execute(() -> this.callback.accept(this.lastSeenState.getContents()));
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

    private String sendToFirstActiveServer(State state) {
        Server n = this.reordered.getNext();
        while (!trySend(n.getServerURL(), state)) {
            n = n.getNext();
        }
        return n.getServerURL();
    }

    private boolean trySend(String thisServer, State state) {
        return this.provider.getTransmitter().send(thisServer, state);
    }

}
