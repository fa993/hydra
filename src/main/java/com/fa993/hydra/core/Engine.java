package com.fa993.hydra.core;

import com.fa993.hydra.api.ConnectionProvider;
import com.fa993.hydra.api.Parcel;
import com.fa993.hydra.exceptions.*;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;

public class Engine {

    private static final int poolTimeout = 5000;

    private static final Logger logger = LoggerFactory.getLogger(Engine.class);

    private static Engine singleton;

    private static final Runnable DO_NOTHING = () -> {
    };

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> EMPTY = new HashMap<>();

    private Configuration configs;

    private ConnectionProvider provider;

    private Integer lastSeenToken;

    private Queue<Integer> proposedNextTokensIssuerId;

    private Server reordered;

    private int connectionTimeout;

    private int heartbeatDelay;

    private Instant lastActiveTime;

    private boolean stopReceiving;

    private boolean stopTransmitting;

    private LinkedBlockingQueue<Integer> tokens;

    private LinkedBlockingQueue<Parcel> orders;

    private Runnable callback;

    private volatile boolean primary;

    private int serversLength;

    //TODO consider changing to single thread executor to maintain temporal integrity of execution callbacks
    private ExecutorService executor = Executors.newCachedThreadPool((r) -> {
        Thread t = new Thread(r, "Hydra-callback-" + System.currentTimeMillis());
        t.setDaemon(true);
        return t;
    });

    private final Map<String, Function<Command, Boolean>> coreActions;

    private final Map<String, Consumer<Command>> applicationActions;

    /**
     * Only use this method if you want to dynamically set the contents and the callback which require the engine to be initialized but this server is not ready to be inserted into the cluster, otherwise use {@link Engine#start(Map, Runnable)}
     * Only use this method in conjunction with {@link Engine#on()}.
     * Do not use {@link Engine#start(Map, Runnable)} with this
     *
     * @param contents The initial contents object. Will only be persisted if this server is primary on start up
     * @param callback the callback which will be run when this server becomes primary
     * @throws NoConfigurationFileException        If there is no configuration file provided
     * @throws MalformedConfigurationFileException If the configuration file is syntactically incorrect
     * @throws InvalidConnectionProviderException  If the ConnectionProvider is not matching the required specifications
     */
    public synchronized static void initialize(Map<String, String> contents, Runnable callback) throws NoConfigurationFileException, MalformedConfigurationFileException, InvalidConnectionProviderException {
        if (singleton == null) {
            singleton = new Engine(contents, callback);
        } else {
            throw new MultipleEngineException();
        }
    }

    /**
     * Makes this server ready for insertion into the cluster
     * Only use this method in conjunction with {@link Engine#initialize(Map, Runnable)}.
     * Do not use {@link Engine#start(Map, Runnable)} with this
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
    public synchronized static void start(Map<String, String> contents, Runnable callback) throws NoConfigurationFileException, MalformedConfigurationFileException, InvalidConnectionProviderException {
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
     * Check if this server is primary
     *
     * @return true if this server is the primary, false otherwise
     * @throws EngineNotInitializedException if the engine has not started yet
     */
    public static boolean isPrimary() {
        if (singleton != null) {
            return singleton.primary;
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
    public static void onPrimary(Runnable callback) {
        if (singleton != null) {
            if (callback == null) {
                callback = DO_NOTHING;
            }
            singleton.callback = callback;
        } else {
            throw new EngineNotInitializedException();
        }
    }

    private Engine(Map<String, String> contents, Runnable callback) throws NoConfigurationFileException, MalformedConfigurationFileException, InvalidConnectionProviderException {
        this.configs = readConfigFile();
        try {
            this.provider = (ConnectionProvider) Class.forName(this.configs.getConnectionProvider()).getConstructor(Configuration.class).newInstance(this.configs);
        } catch (ReflectiveOperationException e) {
            throw new InvalidConnectionProviderException(e);
        }
        Server og = new Server(this.configs.getServers()[(this.configs.getCurrentServerIndex()) % this.configs.getServers().length]);
        this.reordered = og;
        this.serversLength = this.configs.getServers().length;
        for (int i = 1; i < this.serversLength; i++) {
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
        this.tokens = new LinkedBlockingQueue<>();
        this.proposedNextTokensIssuerId = new LinkedBlockingQueue<>();
        if (callback != null) {
            this.callback = callback;
        } else {
            this.callback = DO_NOTHING;
        }
        this.coreActions = new HashMap<>();
        this.coreActions.put(Headers.CURRENT_ACTIVE_CLUSTER_MARKER, t -> {
            t.appendValue(Headers.CURRENT_ACTIVE_CLUSTER, this.reordered.getServerURL());
            return true;
        });
        this.coreActions.put(Headers.FORCE_CORONATION, t -> {
            if (this.lastSeenToken != null && this.lastSeenToken == singleton.configs.getCurrentServerIndex()) {
                this.proposedNextTokensIssuerId.add(Integer.parseInt(t.getValue(Headers.FORCE_CORONATION)));
                return true;
            } else return false;
        });
        this.applicationActions = new HashMap<>();
    }

    public Configuration readConfigFile() throws NoConfigurationFileException, MalformedConfigurationFileException {
        try {
            return mapper.readValue(this.getClass().getClassLoader().getResourceAsStream("waterfall.json"), Configuration.class);
        } catch (JsonParseException e) {
            throw new MalformedConfigurationFileException(e);
        } catch (IOException e) {
            throw new NoConfigurationFileException(e);
        }
    }

    public void run() {
        Thread t1 = new Thread(() -> {
            while (!stopTransmitting || tokens.size() > 0) {
                try {
                    sendToFirstActiveServer(this.tokens.take());
                } catch (InterruptedException e) {
                    logger.debug("Interrupted", e);
                }
            }
        }, "Hydra-Token-Transmitting-Thread");
        Thread t2 = new Thread(() -> {
            while (!stopTransmitting || orders.size() > 0) {
                try {
                    sendToFirstActiveServer(this.orders.take());
                } catch (InterruptedException e) {
                    logger.debug("Interrupted", e);
                }
            }
        }, "Hydra-Parcel-Transmitting-Thread");
        Thread t3 = new Thread(() -> {
            while (!stopReceiving) {
                Integer i = this.provider.getReceiver().receiveToken(this.connectionTimeout);
                if (i == null) {
                    //Timeout
                    if (!this.stopReceiving) {
                        //theOneTrueKingIsYou();
                        this.lastSeenToken = this.configs.getCurrentServerIndex();
                        logger.info("Timed out... will attempt to become primary");
                    } else continue;
                } else {
                    //Check for veto now
                    logger.trace("Received token: " + i);
                    if (this.lastSeenToken != null && this.configs.getCurrentServerIndex() == this.lastSeenToken && (i - this.configs.getCurrentServerIndex() < 0)) {
                        //Split brain hit!
                        logger.trace("Rejected a token to avoid split brain");
                        continue;
                    }
                    //do not veto
                    if (i == this.configs.getCurrentServerIndex()) {
                        //theOneTrueKingIsYouAgain()
                        Integer candidate = this.proposedNextTokensIssuerId.poll();
                        if (candidate == null) {
                            logger.trace("I am Primary");
                            if (!primary) {
                                primary = true;
                                this.executor.execute(() -> callback.run());
                            }
                            try {
                                Thread.sleep(this.heartbeatDelay);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        } else {
                            this.lastSeenToken = candidate;
                            logger.trace("Shifting Primary");
                            primary = false;
                        }
                    } else {
                        //theOneTrueKingIsNotYou()
                        this.lastSeenToken = i;
                        logger.trace("Not Primary");
                    }
                }
                if (this.lastSeenToken != null) {
                    this.tokens.add(this.lastSeenToken);
                }
                this.lastActiveTime = Instant.now();
                stopTransmitting = stopReceiving;
            }
        }, "Hydra-Receive-State-Thread");
        Thread t4 = new Thread(() -> {
            while (!stopReceiving) {
                Command c = this.provider.getReceiver().receiveCommand();
                c.getAllHeaders().forEach(t -> {
                    boolean b1 = this.coreActions.getOrDefault(t, f -> true).apply(c);
                    if (b1) {
                        executor.execute(() -> this.applicationActions.getOrDefault(t, f -> {
                        }).accept(c));
                    }
                });
                this.orders.add(c);
            }
        }, "Hydra-Receive-Command-Thread");
        t1.setDaemon(true);
        t2.setDaemon(true);
        t3.setDaemon(true);
        t4.setDaemon(true);
        t1.start();
        t2.start();
        t3.start();
        t4.start();
    }

    private void sendToFirstActiveServer(Parcel parcel) {
        Server n = this.reordered;
        boolean result;
        int x = 0;
        do {
            n = n.getNext();
            result = trySend(n.getServerURL(), parcel);
        } while (!result && x < this.serversLength);
        if (!result) {
            logger.trace("Could not send parcel");
        } else {
            logger.trace("Sent parcel to " + n.getServerURL());
        }
    }

    private void sendToFirstActiveServer(Integer i) {
        Server n = this.reordered;
        boolean result;
        int x = 0;
        do {
            n = n.getNext();
            result = trySend(n.getServerURL(), i);
            x++;
        } while (!result && x < this.serversLength);
        if (!result) {
            logger.trace("Could not send token");
        } else {
            logger.trace("Sent token to " + n.getServerURL());
        }
    }

    private boolean trySend(String thisServer, Parcel parcel) {
        return this.provider.getTransmitter().send(thisServer, parcel);
    }

    private boolean trySend(String thisServer, Integer i) {
        return this.provider.getTransmitter().send(thisServer, i);
    }

}
