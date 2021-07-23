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

    private Token lastSeenToken;

    private Queue<Integer> proposedNextTokensIssuerId;

    private Server reordered;

    private int connectionTimeout;

    private int heartbeatDelay;

    private Instant lastActiveTime;

    private boolean stopReceiving;

    private boolean stopTransmitting;

    private LinkedBlockingQueue<WorkOrder> orders;

    private Runnable callback;

//    private Map<String, String> contents;

    //TODO consider changing to single thread executor to maintain temporal integrity of execution callbacks
    private ExecutorService executor = Executors.newCachedThreadPool((r) -> {
        Thread t = new Thread(r, "Hydra-callback-" + System.currentTimeMillis());
        t.setDaemon(true);
        return t;
    });

    private final Function<Token, Boolean> validator;

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

//    /**
//     * Get the most recent contents object
//     *
//     * @return the contents object which may or may not have originated from this server
//     * @throws EngineNotInitializedException if the engine has not started yet
//     */
//    public static Map<String, String> contents() {
//        if (singleton != null) {
//            return singleton.lastSeenState.getContents();
//        } else {
//            throw new EngineNotInitializedException();
//        }
//    }

    /**
     * Check if this server is primary
     *
     * @return true if this server is the primary, false otherwise
     * @throws EngineNotInitializedException if the engine has not started yet
     */
    public static boolean isPrimary() {
        if (singleton != null) {
            return singleton.lastSeenToken.getTokenIssuerIndex() == singleton.configs.getCurrentServerIndex();
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

//    /**
//     * Use this method to update the contents object to better reflect the state of this server
//     *
//     * @param contents the contents which will be passed to other servers if this server is primary
//     */
//    public static void pushContents(Map<String, String> contents) {
//        if (singleton != null) {
//            if (contents == null) {
//                contents = EMPTY;
//            }
//            singleton.contents = contents;
//        } else {
//            throw new EngineNotInitializedException();
//        }
//    }

    private Engine(Map<String, String> contents, Runnable callback) throws NoConfigurationFileException, MalformedConfigurationFileException, InvalidConnectionProviderException {
        Token.initialize(10 + 1);
        Transaction.initialize(10);
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
        this.proposedNextTokensIssuerId = new LinkedBlockingQueue<>();
//        if (contents != null) {
//            this.contents = contents;
//        } else {
//            this.contents = EMPTY;
//        }
        if (callback != null) {
            this.callback = callback;
        } else {
            this.callback = DO_NOTHING;
        }
        this.validator = (st) -> {
            if (this.lastSeenToken != null && this.configs.getCurrentServerIndex() == this.lastSeenToken.getTokenIssuerIndex() && !(this.configs.getCurrentServerIndex() == st.getTokenIssuerIndex())) {
                //Split brain hit!
                int x1 = st.getTokenIssuerIndex();
                int x2 = this.configs.getCurrentServerIndex();
                //only reject flow from one side
                return x1 - x2 >= 0;
            } else {
                return true;
            }

        };
        this.provider.getReceiver().configureValidatorForState(validator);
        this.coreActions = new HashMap<>();
        this.coreActions.put(Headers.CURRENT_ACTIVE_CLUSTER_MARKER, t -> {
            t.appendValue(Headers.CURRENT_ACTIVE_CLUSTER, this.reordered.getServerURL());
            return true;
        });
        this.coreActions.put(Headers.FORCE_CORONATION, t -> {
            if (this.lastSeenToken != null && this.lastSeenToken.getTokenIssuerIndex() == singleton.configs.getCurrentServerIndex()) {
                this.proposedNextTokensIssuerId.add(Integer.parseInt(t.getValue(Headers.FORCE_CORONATION)));
                return true;
            } else return false;
        });
        this.applicationActions = new HashMap<>();
    }

    public Configuration readConfigFile() throws NoConfigurationFileException, MalformedConfigurationFileException {
        try {
            return mapper.readValue(this.getClass().getClassLoader().getResourceAsStream("waterfall.json"), Configuration.class);
//            return mapper.readValue(new File("src/main/resources/waterfall.json"), Configuration.class);
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
                    Transaction transaction = Transaction.getTransaction(poolTimeout);
                    sendToFirstActiveServer(transaction, order.associatedParcel);
                    switch (transaction.getResult()) {
                        case VETOED:
                            logger.trace("The send was vetoed");
                            order.status = Status.VETOED;
                            break;
                        case SUCCESS:
                            logger.trace("Sent parcel to " + transaction.getServerTo());
                            order.status = Status.SUCCESS;
                            break;
                    }
                    executor.execute(() -> order.get(order.status).run());
                    Transaction.reclaimTransaction(transaction);
                } catch (InterruptedException e) {
                    logger.debug("Interrupted", e);
                } catch (ObjectPoolExhaustedException ex) {
                    logger.debug("Object pool has been exhausted");
                    Engine.stop();
                }
            }
        }, "Hydra-Transmitting-Thread");
        Thread t2 = new Thread(() -> {
            while (!stopReceiving) {
                Transaction tr = null;
                try {
                    tr = this.provider.getReceiver().receiveToken(this.connectionTimeout);
                } catch (ObjectPoolExhaustedException e) {
                    e.printStackTrace();
                    Engine.stop();
                    continue;
                }
                Parcel p = tr.getParcel();
                TransactionResult r = tr.getResult();
                Transaction.reclaimTransaction(tr);
                switch (r) {
                    case SUCCESS:
                        Token token = (Token) p;
                        logger.trace("Received state: " + token.toLog());
                        long execTime = System.currentTimeMillis();
                        Runnable onSuccess = () -> {
                        };
                        if (token.equals(this.lastSeenToken)) {
                            //theOneTrueKingIsYouAgain()
                            Token.reclaimToken(this.lastSeenToken);
                            token.reissue();
                            this.lastSeenToken = token;
                            Integer candidate = this.proposedNextTokensIssuerId.poll();
                            if (candidate == null) {
                                logger.trace("Retained Primary: " + this.lastSeenToken.toLog());
                            } else {
                                token.setTokenId(candidate);
                                logger.trace("Shifting Primary: " + this.lastSeenToken.toLog());
                            }
                            try {
                                Thread.sleep(this.heartbeatDelay);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            //execTime = System.currentTimeMillis() + this.heartbeatDelay;
                        } else if (token.getTokenIssuerIndex() == this.configs.getCurrentServerIndex()) {
                            logger.trace("Got Primary");
                            token.reissue();
                            if (this.lastSeenToken.getTokenIssuerIndex() != this.configs.getCurrentServerIndex()) {
                                onSuccess = () -> this.callback.run();
                            }
                            Token.reclaimToken(this.lastSeenToken);
                            this.lastSeenToken = token;
                            //execTime = System.currentTimeMillis() + this.heartbeatDelay;
                            try {
                                Thread.sleep(this.heartbeatDelay);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        } else {
                            //theOneTrueKingIsNotYou()
                            Token.reclaimToken(this.lastSeenToken);
                            this.lastSeenToken = token;
                            logger.trace("Not Primary");
                        }
                        WorkOrder w = new WorkOrder(execTime, this.lastSeenToken, Status.PENDING);
                        w.on(Status.SUCCESS, onSuccess);
                        this.orders.add(w);
                        break;
                    case TIMEOUT:
                        if (!this.stopReceiving) {
                            //theOneTrueKingIsYou();
                            if (this.lastSeenToken != null) {
                                Token.reclaimToken(this.lastSeenToken);
                            }
                            try {
                                this.lastSeenToken = Token.getToken(poolTimeout);
                            } catch (ObjectPoolExhaustedException e) {
                                logger.debug("Object pool has been exhausted");
                                Engine.stop();
                                continue;
                            }
                            this.lastSeenToken.reissue();
                            this.lastSeenToken.setTokenIssuerIndex(this.configs.getCurrentServerIndex());
                            logger.info("Became Primary due to timeout: " + this.lastSeenToken.toLog());
                            WorkOrder w1 = new WorkOrder(System.currentTimeMillis(), this.lastSeenToken, Status.PENDING);
                            w1.on(Status.SUCCESS, () -> this.callback.run());
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
        }, "Hydra-Receive-State-Thread");
        Thread t3 = new Thread(() -> {
            while (!stopReceiving) {
                Transaction transaction = null;
                try {
                    transaction = this.provider.getReceiver().receiveCommand();
                } catch (ObjectPoolExhaustedException e) {
                    e.printStackTrace();
                }
                Parcel p = transaction.getParcel();
                TransactionResult r = transaction.getResult();
                Transaction.reclaimTransaction(transaction);
                switch (r) {
                    case SUCCESS:
                        //handleCommand
                        Command command = (Command) p;
                        command.getAllHeaders().forEach(t -> {
                            boolean b1 = this.coreActions.getOrDefault(t, f -> true).apply(command);
                            if (b1) {
                                executor.execute(() -> this.applicationActions.getOrDefault(t, f -> {
                                }).accept(command));
                            }
                        });
                        this.orders.add(new WorkOrder(System.currentTimeMillis(), command, Status.PENDING));
                        break;
                    case FAILURE:
                        logger.trace("Unknown Failure occurred");
                        break;
                    default:
                        logger.trace("This is an error... receiver can only return failure or success according to the specs");
                }
            }
        }, "Hydra-Receive-Command-Thread");
        t1.setDaemon(true);
        t2.setDaemon(true);
        t3.setDaemon(true);
        t1.start();
        t2.start();
        t3.start();
    }

    private void sendToFirstActiveServer(Transaction buffer, Parcel parcel) {
        Server n = this.reordered;
        TransactionResult result;
        do {
            n = n.getNext();
            result = trySend(n.getServerURL(), parcel);
        } while (result == TransactionResult.FAILURE || result == TransactionResult.TIMEOUT);
        buffer.setResult(result);
        buffer.setServerTo(n.getServerURL());
    }

    private TransactionResult trySend(String thisServer, Parcel parcel) {
        return this.provider.getTransmitter().send(thisServer, parcel);
    }

}
