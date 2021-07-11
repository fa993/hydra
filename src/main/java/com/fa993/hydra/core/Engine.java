package com.fa993.hydra.core;

import com.fa993.hydra.api.ConnectionProvider;
import com.fa993.hydra.exceptions.*;
import com.fa993.hydra.misc.Utils;
import com.fasterxml.jackson.core.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;

public class Engine {

    private static Logger logger = LoggerFactory.getLogger(Engine.class);

    private static Engine singleton;

    private static final Consumer<Map<String, String>> DO_NOTHING = t -> {
    };

    private static final Map<String, String> EMPTY = new HashMap<>();

    private Configuration configs;

    private ConnectionProvider provider;

    private State lastSeenState;

    private Server reordered;

    private int connectionTimeout;

    private int heartbeatDelay;

    private Instant lastActiveTime;

    private boolean stopReceiving;

    private boolean stopTransmitting;

    private LinkedBlockingQueue<WorkOrder> orders;

    private Consumer<Map<String, String>> callback;

    private Map<String, String> contents;

    private ExecutorService executor = Executors.newSingleThreadExecutor((r) -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    private final Function<State, Boolean> validator;

    /**
     * Only use this method if you want to dynamically set the contents and the callback which require the engine to be initialized but this server is not ready to be inserted into the cluster, otherwise use {@link Engine#start(Map, Consumer)}
     * Only use this method in conjunction with {@link Engine#on()}.
     * Do not use {@link Engine#start(Map, Consumer)} with this
     *
     * @param contents The initial contents object. Will only be persisted if this server is primary on start up
     * @param callback the callback which will be run when this server becomes primary
     * @throws NoConfigurationFileException        If there is no configuration file provided
     * @throws MalformedConfigurationFileException If the configuration file is syntactically incorrect
     * @throws InvalidConnectionProviderException  If the ConnectionProvider is not matching the required specifications
     */
    public synchronized static void initialize(Map<String, String> contents, Consumer<Map<String, String>> callback) throws NoConfigurationFileException, MalformedConfigurationFileException, InvalidConnectionProviderException {
        if (singleton == null) {
            singleton = new Engine(contents, callback);
        } else {
            throw new MultipleEngineException();
        }
    }

    /**
     * Makes this server ready for insertion into the cluster
     * Only use this method in conjunction with {@link Engine#initialize(Map, Consumer)}.
     * Do not use {@link Engine#start(Map, Consumer)} with this
     */
    public static void on() {
        if (singleton != null) {
            singleton.run();
        } else {
            throw new EngineNotInitializedException();
        }
    }

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
     * @throws EngineNotInitializedException if the engine has not started yet
     */
    public static void stop() {
        if (singleton != null) {
            singleton.stopReceiving = true;
        } else {
            throw new EngineNotInitializedException();
        }
    }

    /**
     * Get the most recent contents object
     *
     * @return the contents object which may or may not have originated from this server
     * @throws EngineNotInitializedException if the engine has not started yet
     */
    public static Map<String, String> contents() {
        if (singleton != null) {
            return singleton.lastSeenState.getContents();
        } else {
            throw new EngineNotInitializedException();
        }
    }

    /**
     * Check if this server is primary
     *
     * @return true if this server is the primary, false otherwise
     * @throws EngineNotInitializedException if the engine has not started yet
     */
    public static boolean isPrimary() {
        if (singleton != null) {
            return singleton.lastSeenState.getOwnerURL().equals(singleton.reordered.getServerURL());
        } else {
            throw new EngineNotInitializedException();
        }
    }

    /**
     * Only use this method if the callback may change dynamically, otherwise use the parameter in the constructor
     *
     * @param callback The new callback when this server becomes primary
     * @throws EngineNotInitializedException if the engine has not started yet
     */
    public static void onPrimary(Consumer<Map<String, String>> callback) {
        if (singleton != null) {
            if (callback == null) {
                callback = DO_NOTHING;
            }
            singleton.callback = callback;
        } else {
            throw new EngineNotInitializedException();
        }
    }

    /**
     * Use this method to update the contents object to better reflect the state of this server
     *
     * @param contents the contents which will be passed to other servers if this server is primary
     */
    public static void pushContents(Map<String, String> contents) {
        if (singleton != null) {
            if (contents == null) {
                contents = EMPTY;
            }
            singleton.contents = contents;
        } else {
            throw new EngineNotInitializedException();
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
        this.orders = new LinkedBlockingQueue<>();
        if (contents != null) {
            this.contents = contents;
        } else {
            this.contents = EMPTY;
        }
        if (callback != null) {
            this.callback = callback;
        } else {
            this.callback = DO_NOTHING;
        }
        this.validator = (s) -> {
            if (this.lastSeenState != null && this.reordered.getServerURL().equals(this.lastSeenState.getOwnerURL()) && !this.reordered.getServerURL().equals(s.getOwnerURL())) {
                //Split brain hit!
                return false;
            } else {
                return true;
            }
        };
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
        Thread t1 = new Thread(() -> {
            while (!stopTransmitting || orders.size() > 0) {
                try {
                    WorkOrder order = this.orders.take();
                    long t = order.executeAfter - System.currentTimeMillis();
                    if (t > 0) {
                        Thread.sleep(t);
                    }
                    Transaction transaction = sendToFirstActiveServer(order.associatedState);
                    switch (transaction.getResult()) {
                        case VETOED:
                            logger.trace("Vetoed sending of state");
                            order.status = Status.VETOED;
                            break;
                        case SUCCESS:
                            logger.trace("Sent state to " + transaction.getServerTo());
                            order.status = Status.SUCCESS;
                            break;
                    }
                    executor.execute(() -> order.get(order.status).run());
                } catch (InterruptedException e) {
                    logger.debug("Interrupted", e);
                }
            }
        });
        Thread t2 = new Thread(() -> {
            while (!stopReceiving) {
                Transaction tr = this.provider.getReceiver().receive(this.connectionTimeout, this.validator);
                switch (tr.getResult()) {
                    case SUCCESS:
                        State newState = tr.getState();
                        logger.trace("Received state: " + newState.toLog());
                        long execTime;
                        if (newState.equals(this.lastSeenState)) {
                            //theOneTrueKingIsYouAgain()
                            this.lastSeenState = newState.reissue();
                            this.lastSeenState.setContents(this.contents);
                            logger.trace("Retained Primary: " + this.lastSeenState.toLog());
                            execTime = System.currentTimeMillis() + this.heartbeatDelay;
                        } else {
                            //theOneTrueKingIsNotYou()
                            this.lastSeenState = newState;
                            logger.trace("Not Primary");
                            execTime = System.currentTimeMillis();
                        }
                        WorkOrder w = new WorkOrder(execTime, this.lastSeenState, Status.PENDING);
                        this.orders.add(w);
                        break;
                    case TIMEOUT:
                        if (!this.stopReceiving) {
                            //theOneTrueKingIsYou();
                            this.lastSeenState = new State(this.reordered.getServerURL(), this.contents);
                            logger.info("Became Primary due to timeout: " + this.lastSeenState.toLog());
                            WorkOrder w1 = new WorkOrder(System.currentTimeMillis(), this.lastSeenState, Status.PENDING);
                            w1.on(Status.SUCCESS, () -> this.callback.accept(this.lastSeenState.getContents()));
                            this.orders.add(w1);
                        }
                        break;
                    case VETOED:
                        logger.trace("Rejected a state to avoid split brain");
                        break;
                    case FAILURE:
                        logger.trace("Some unknown failure occurred");
                        break;
                }
                this.lastActiveTime = Instant.now();
                stopTransmitting = stopReceiving;
            }
        });
        t1.setDaemon(true);
        t2.setDaemon(true);
        t1.start();
        t2.start();
    }

    private Transaction sendToFirstActiveServer(State state) {
        Server n = this.reordered;
        TransactionResult result = null;
        do {
            n = n.getNext();
            result = trySend(n.getServerURL(), state);
        } while (result == TransactionResult.FAILURE || result == TransactionResult.TIMEOUT);
        return new Transaction(n.getServerURL(), result);
    }

    private TransactionResult trySend(String thisServer, State state) {
        return this.provider.getTransmitter().send(thisServer, state);
    }

}