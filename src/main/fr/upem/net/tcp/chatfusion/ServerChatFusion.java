package fr.upem.net.tcp.chatfusion;

import fr.upem.net.tcp.chatfusion.Packet.*;
import fr.upem.net.tcp.chatfusion.Reader.PacketReader;
import fr.upem.net.tcp.chatfusion.Reader.Reader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerChatFusion {

    private final HashMap<Client, Context> connectedClients = new HashMap<>();
    private final HashMap<String, Context> connectedServer = new HashMap<>();
    private static final int BUFFER_SIZE = 1_024;
    private static final Logger logger = Logger.getLogger(ServerChatFusion.class.getName());
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private Context leader;
    private final String name;
    private final Thread console;

    public ServerChatFusion(int port, String name) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        // switch server to non-blocking mode
        serverSocketChannel.configureBlocking(false);
        selector = Selector.open();
        this.name = name;
        // initialize by default the leader being the server itself
        this.leader = null;
        this.console = new Thread(this::consoleRun);
    }

    /**
     * Thread to read command on terminal
     */
    private void consoleRun() {
        try (var scanner = new Scanner(System.in)) {
            while (scanner.hasNextLine()) {
                try {
                    var msg = scanner.nextLine();
                    sendCommand(msg);
                } catch (InterruptedException e) {
                    logger.info("Console thread has been interrupted");
                }
            }
        }
        logger.info("Console thread stopping");
    }

    /**
     * Send instructions to the selector via a BlockingQueue and wake it up
     */
    private void sendCommand(String msg) throws InterruptedException {
        synchronized (console) {
            String[] cmd = msg.split(" ");
            switch (cmd[0]) {
                case "FUSION" -> {
                    try {
                        var inetSA = new InetSocketAddress(cmd[1], Integer.parseInt(cmd[2]));
                        var sc = SocketChannel.open();

                        sc.configureBlocking(false);
                        sc.connect(inetSA);

                        var key = sc.register(selector, SelectionKey.OP_CONNECT);
                        var context = new Context(this, key);
                        context.requestFusion(inetSA);
                    } catch (IOException e) {
                        logger.info("Channel has been closed");
                    }
                }
                case "INFO" -> System.out.println(connectedClients.keySet());

                default -> System.out.println("Unknown command typed");
            }
        }
    }


    /**
     * @param login String
     * @return boolean
     */
    private boolean isConnect(String login) {
        for (var client : connectedClients.keySet())
            if (client.checkIsLogin(login))
                return true;
        return false;
    }

    public void launch() throws IOException {
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        this.console.start();

        while (!Thread.interrupted()) {
            Helpers.printKeys(selector);
            try {
                selector.select(this::treatKey);
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
        }
    }

    private void treatKey(SelectionKey key) {
        Helpers.printSelectedKey(key);
        try {
            if (key.isValid() && key.isAcceptable()) {
                doAccept(key);
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
        try {
            if (key.isValid() && key.isConnectable()) {
                ((Context) key.attachment()).doConnect();
            }
            if (key.isValid() && key.isWritable()) {
                ((Context) key.attachment()).doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                ((Context) key.attachment()).doRead();
            }
        } catch (IOException e) {
            logger.log(Level.INFO, "Connection closed with client due to IOException", e);
            silentlyClose(key);
        }
    }

    private void doAccept(SelectionKey key) throws IOException {
        SocketChannel sc = serverSocketChannel.accept();
        if (sc == null) {
            logger.warning("liar accept");
            return;
        }
        sc.configureBlocking(false);
        var clientKey = sc.register(selector, SelectionKey.OP_READ);
        clientKey.attach(new Context(this, clientKey));
    }

    private void silentlyClose(SelectionKey key) {
        Channel sc = key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    /**
     * Add a message to all connected clients queue
     *
     * @param packet Message
     */
    private void broadcastClient(Packet packet) {
        for (var value : connectedClients.values()) {
            value.queueMessage(packet);
        }
    }

    /**
     * Add a message to all connected server queue
     *
     * @param packet Message
     */
    private void broadcastServer(Packet packet) {
        connectedServer.forEach((key, value) -> value.queueMessage(packet));
    }

    private List<String> getListConnectedServer() {
        return connectedServer.keySet().stream().toList();
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 2) {
            usage();
            return;
        }
        new ServerChatFusion(Integer.parseInt(args[0]), args[1]).launch();
    }

    private static void usage() {
        System.out.println("Usage : ServerSumBetter port");
    }


    // #################### CLIENT #################### //

    private record Client(String login) {
        private boolean checkIsLogin(String login) {
            return this.login.equals(login);
        }

        @Override
        public String toString() {
            return login;
        }
    }


    // #################### CONTEXT #################### //

    private class Context {
        private final SelectionKey key;
        private final SocketChannel sc;
        private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
        private final ArrayDeque<Packet> queue = new ArrayDeque<>();
        private final PacketReader packetReader = new PacketReader();
        private final ServerChatFusion server;
        private boolean closed = false;
        private Packet packet;

        private Context(ServerChatFusion server, SelectionKey key) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            this.server = server;
        }

        @Override
        public String toString() {
            try {
                return "[SocketAddress: " + sc.getRemoteAddress().toString() + ", leader: " + leader + "]";
            } catch (IOException e) {
                return "";
            }
        }

        /**
         * Process the content of bufferIn. <br>
         * The convention is that bufferIn is in write-mode
         * before the call to process and after the call
         */
        private void processIn() {
            for (; ; ) {
                Reader.ProcessStatus status = packetReader.process(bufferIn);

                switch (status) {
                    case DONE -> {
                        logger.info("DONE");
                        packet = packetReader.get();
                        switch (packet.opCodeGet()) {
                            case 0, 1 -> {
                                connection();
                                return;
                            }
                            case 4 -> {
                                publicMessage();
                                return;
                            }
                            case 8 -> {
                                initFusion();
                                return;
                            }
                            case 14 -> {
                                fusionMerge();
                                return;
                            }
                        }
                    }
                    case REFILL -> {
                        logger.info("REFILL");
                        return;
                    }

                    case ERROR -> {
                        logger.info("ERROR");
                        silentlyClose();
                    }
                }
                packetReader.reset();
            }
        }

        private void requestFusion(SocketAddress sa) {
            var packetFusionInit = new PacketFusionInit(8, name, sa, connectedServer.size(), getListConnectedServer());
            queueMessage(packetFusionInit);
        }

        private void fusionMerge() {
            try {
                SocketAddress sa = (SocketAddress) packet.components().get(0);
                var sc = SocketChannel.open();
                sc.bind(sa).configureBlocking(false);
                var key = sc.register(selector, SelectionKey.OP_CONNECT);
                leader = new Context(this.server, key);
                var packet = new PacketString(15, name);
                leader.queueMessage(packet);
            } catch (IOException e) {
                logger.info("Channel has been closed");
            }

        }

        /**
         *
         */
        private void initFusion() {
            var packetFusion = (PacketFusionInit) packet;
            // Test if actual server == leader
            if (leader == null) {
                // Check that both servers doesn't have a similar server linked to themselves
                if (!hasServerInCommon(packetFusion.components())) {
                    try {
                        var connectedServerName = getListConnectedServer();
                        var socketAddress = sc.getLocalAddress();
                        var packetFusionInit = new PacketFusionInit(9, name, socketAddress, connectedServer.size(), connectedServerName);
                        queueMessage(packetFusionInit);

                        switchLeaderName(packetFusion.name());

                        fusion(packetFusion);
                    } catch (IOException e) {
                        logger.info("fail socketAddress");
                    }
                } else {
                    try {
                        var leaderAdr = leader.sc.getRemoteAddress();
                        var packetToReturn = new PacketSocketAddress(11, leaderAdr);
                        queueMessage(packetToReturn);
                    } catch (IOException e) {
                        logger.info("Error");
                    }
                }
            }
        }

        private void fusion(PacketFusionInit packet) {
            if (leader != null) {
                try {
                    var packetChangeLeader = new PacketSocketAddress(14, leader.sc.getRemoteAddress());
                    queueMessage(packetChangeLeader);
                } catch (IOException e) {
                    logger.info("Channel was closed");
                }
            } else {
                connectedServer.put(packet.name(), this);
            }
        }

        private void switchLeaderName(String serverName) {
            if ((name.compareTo(serverName) > 0))
                leader = this;
            else
                leader = null;
        }

        private boolean hasServerInCommon(List<String> requestServers) {
            for (var serv : requestServers) {
                var entrySet = connectedServer.entrySet();
                for (var server : entrySet)
                    if (server.getKey().equals(serv)) return true;
            }
            return false;
        }

        /**
         *
         */
        private void publicMessage() {
            var nameServer = packet.components().get(0);
            String login = (String) packet.components().get(1);
//            var message = packet.components().get(2);

            if (nameServer.equals(name)) {
                if (isConnect(login)) {
                    broadcastClient(packet);
                } else {
                    logger.warning("Client " + login + " isn't connected to the server");
                }
            } else {
                // Test if server == leader
                if (leader == null) {
                    // Yes, send to connected server
                    connectedServer.forEach((key, value) -> {
                        if (!key.equals(name)) {
                            value.queueMessage(packet);
                        }
                    });
                } else {
                    // No, send to leader
                    leader.queueMessage(packet);
                }
            }
        }

        /**
         *
         */
        public void connection() {
            String login = (String) packet.components().get(0);
            logger.info(login);

            if (isConnect(login)) {
                var packetRefusal = new PacketString(3, new ArrayList<>());
                queueMessage(packetRefusal);
            } else {
                connectedClients.put(new Client(login), this);
                connectionAccepted();
            }
        }

        private void connectionAccepted() {
            var packetAccepted = new PacketString(2, name);
            queueMessage(packetAccepted);
        }

        /**
         * Add a message to the message queue, tries to fill bufferOut and updateInterestOps
         *
         * @param packet Message
         */
        public void queueMessage(Packet packet) {
            queue.add(packet);
            processOut();
            updateInterestOps();
        }

        /**
         * Try to fill bufferOut from the message queue
         */
        private void processOut() {
            var previewMsg = queue.peek();
            while (!queue.isEmpty() && bufferOut.remaining() >= previewMsg.size()) {
                var fullMsg = queue.poll();
                if (fullMsg == null) return;
                bufferOut.put(fullMsg.generateByteBuffer().flip());
            }
        }

        /**
         * Update the interestOps of the key looking only at values of the boolean
         * closed and of both ByteBuffers. <br>
         * The convention is that both buffers are in write-mode before the call to
         * updateInterestOps and after the call. <br>
         * It is assumed that process has been called just before updateInterestOps.
         */
        private void updateInterestOps() {
            var ops = 0;
            if (bufferIn.hasRemaining() && !closed) ops |= SelectionKey.OP_READ;

            if (bufferOut.position() != 0) ops |= SelectionKey.OP_WRITE;

            if (ops == 0) silentlyClose();
            else key.interestOps(ops);
        }

        private void silentlyClose() {
            try {
                sc.close();
            } catch (IOException e) {
                // ignore exception
            }
        }

        /**
         * Performs the read action on sc
         * <p>
         * The convention is that both buffers are in write-mode before the call to
         * doRead and after the call
         *
         * @throws IOException Is thrown if the SocketChannel <b>sc</b> is closed while reading from it
         */
        private void doRead() throws IOException {
            if (sc.read(bufferIn) == -1) closed = true;
            if (!closed) {
                processIn();
            }
            updateInterestOps();
        }

        /**
         * Performs the write action on sc
         * <p>
         * The convention is that both buffers are in write-mode before the call to
         * doWrite and after the call
         *
         * @throws IOException Is thrown if the SocketChannel <b>sc</b> is closed while writing in it
         */
        private void doWrite() throws IOException {
            bufferOut.flip();
            sc.write(bufferOut);
            bufferOut.compact();
            processOut();
            updateInterestOps();
        }

        public void doConnect() throws IOException {
            if (!sc.finishConnect()) {
                logger.warning("Bad thing happened");
                return;
            }
            updateInterestOps();
        }
    }
}
