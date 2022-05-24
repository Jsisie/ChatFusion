package fr.upem.net.tcp.chatfusion;

import fr.upem.net.tcp.chatfusion.Packet.*;
import fr.upem.net.tcp.chatfusion.Reader.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerChatFusion {

    private final List<Client> connectedClients = new ArrayList<>();
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
        try {
            try (var scanner = new Scanner(System.in)) {
                while (scanner.hasNextLine()) {
                    var msg = scanner.nextLine();
                    sendCommand(msg);
                }
            }
            logger.info("Console thread stopping");
        } catch (InterruptedException e) {
            logger.info("Console thread has been interrupted");
        }
    }

    /**
     * Send instructions to the selector via a BlockingQueue and wake it up
     *
     * @throws InterruptedException
     */
    private void sendCommand(String msg) throws InterruptedException {
        synchronized (console) {
            String[] cmd = msg.split(" ");
            switch(cmd[0]) {
                case "FUSION" -> {
                    // TODO - doesn't work, we end up in the IOException
                    // TODO - remove debug comments here
                    System.out.println("0");
                    try {
                        var inetSA = new InetSocketAddress(cmd[1], Integer.parseInt(cmd[2]));
                        var sc = SocketChannel.open();
                        sc.bind(inetSA);
                        sc.configureBlocking(false);
                        var key = sc.register(selector, SelectionKey.OP_CONNECT);
                        var context = new Context(this, key);
                        context.requestFusion();
                    } catch (IOException e) {
                        logger.info("Channel has been closed");
                    }
                }
                default -> System.out.println("Unknown command typed");
            }
        }
    }


    /**
     * @param login String
     * @return boolean
     */
    private boolean IsConnect(String login) {
        for (var client : connectedClients) {
            if (client.checkIsLogin(login)) return true;
        }
        return false;
    }

    public void launch() throws IOException {
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        this.console.start();
        while (!Thread.interrupted()) {
            Helpers.printKeys(selector); // for debug
            System.out.println("Starting select");
            try {
                selector.select(this::treatKey);
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
            System.out.println("Select finished");
        }
    }

    private void treatKey(SelectionKey key) {
        Helpers.printSelectedKey(key); // for debug
        try {
            if (key.isValid() && key.isAcceptable()) {
                doAccept(key);
            }
        } catch (IOException ioe) {
            // lambda call in select requires to tunnel IOException
            throw new UncheckedIOException(ioe);
        }
        try {
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
            return; // the selector gave a bad hint
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
    private void broadcast(Packet packet) {
        // TODO - remove debug comments here
        System.out.println("inside broadcast");
        var keys = selector.keys();
        for (var key : keys) {
            var attach = key.attachment();
            if (attach == null) continue;
            var context = (Context) attach;
            // TODO - remove debug comments here
            System.out.println("Just before queueMessage");
            context.queueMessage(packet);
        }
    }

    private void broadcastClient(Packet packet) {
        for (var client : connectedClients) {
            client.context.queueMessage(packet);
        }
    }

    private void broadcastServer(Packet packet) {
        connectedServer.forEach((key, value) -> {
            value.queueMessage(packet);
        });
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

    private record Client(String login, Context context) {
        private boolean checkIsLogin(String login) {
            return this.login.equals(login);
        }

        @Override
        public String toString() {
            return login + ", " + context.toString();
        }
    }


    // #################### CONTEXT #################### //

    private class Context {
        private final SelectionKey key;
        private final SocketChannel sc;
        private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
        private final ArrayDeque<Packet> queue = new ArrayDeque<>();
        private final Charset cs = StandardCharsets.UTF_8;
        private final MessageReader msgReader = new MessageReader();
        private final StringReader stringReader = new StringReader();
        private final ConnectReader connectReader = new ConnectReader();
        private final PublicMessageReader publicMessageReader = new PublicMessageReader();
        private final SocketAddressReader socketAddressReader = new SocketAddressReader();

        private final FusionInitReader fusionInitReader = new FusionInitReader(8);
        private final FusionInitReader fusionInitReaderOK = new FusionInitReader(9);
        private final ServerChatFusion server; // we could also have Context as an instance class, which would naturally
        // give access to ServerChatInt.this
        private boolean closed = false;

        Reader.ProcessStatus status;

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
                // TODO - remove debug comments here
                System.out.println("\n");
                // TODO - ERROR here, always call switch with the same value for opCode until exception thrown
                logger.info("DONE");
                int opcode = bufferIn.getInt();
                // TODO - REMOVE THE BELOW LINE ABSOLUTLY
                opcode = 4;
                // TODO - remove debug comments here
                System.out.println("opCode = " + opcode);
                switch (opcode) {
                    case 0, 1 -> connection();
                    case 4 -> publicMessage();
                    case 8 -> initFusion();
                    case 14 -> fusionMerge();
                }
            }
        }

        private void requestFusion() {
            var packetFusionInit = new PacketFusionInit(8, name, socketAddressReader.get(), connectedServer.size(), getListConnectedServer());
            queueMessage(packetFusionInit);
        }

        private void fusionMerge() {
            status = socketAddressReader.process(bufferIn);
            switch (status) {
                case DONE -> {
                    try {
                        var sa = socketAddressReader.get();
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
                case REFILL -> logger.info("REFILL");

                case ERROR -> {
                    logger.info("ERROR");
                    silentlyClose();
                }
            }
        }

        /**
         *
         */
        private void initFusion() {
            status = fusionInitReader.process(bufferIn);
            switch (status) {
                case DONE -> {
                    // get packet from Reader
                    var packet = fusionInitReader.get();
                    // Test if actual server == leader
                    if (leader == null) {
                        // Check that both servers doesn't have a similar server linked to themselves
                        if (!hasServerInCommon(packet.components())) {
                            try {
                                var connectedServerName = getListConnectedServer();
                                var socketAddress = sc.getLocalAddress();
                                var packetFusionInit = new PacketFusionInit(9, name, socketAddress, connectedServer.size(), connectedServerName);
                                queueMessage(packetFusionInit);

                                switchLeaderName(packet.GetName());

                                fusion(packet);
                            } catch (IOException e) {
                                logger.info("fail socketAddress");
                                return;
                            }
                        } else {
                            // TODO - send packet (11)
                        }
                    }
                }
                case REFILL -> logger.info("REFILL");

                case ERROR -> {
                    logger.info("ERROR");
                    silentlyClose();
                }
            }
        }

        private void fusion(PacketFusionInit packet) {
            if (leader != null) {
                try {
                    // Send packet 14
                    var packetChangeLeader = new PacketSocketAddress(14, leader.sc.getRemoteAddress());
                    queueMessage(packetChangeLeader);
                } catch (IOException e) {
                    logger.info("Channel was closed");
                    return;
                }
            } else {
                connectedServer.put(packet.GetName(), this);
            }
        }

        private void switchLeaderName(String serverName) {
            if ((name.compareTo(serverName) > 0)) leader = this;
            else leader = null;
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
            // TODO - remove debug comments here
            System.out.println("publicMessage()");
            status = publicMessageReader.process(bufferIn);
            switch (status) {
                case DONE -> {
                    // send buffer to all connected clients
                    var packet = publicMessageReader.get();

                    var nameServer = packet.components().get(0);
                    var login = packet.components().get(1);
                    var message = packet.components().get(2);

                    // TODO - REMOVE ALL THE BELOW LINE ABSOLUTELY
                    nameServer = "ChatFusion";
                    login = "toto";
                    // TODO - remove debug comments here
                    System.out.println("nameServer = " + nameServer);
                    System.out.println("login = " + login);
                    System.out.println("message = " + message);
                    Message msg = new Message(login, message);
                    // TODO - remove debug comments here
                    System.out.println("before broadcast");
                    if (nameServer.equals(name)) {
                        // TODO - remove debug comments here
                        System.out.println("in if");

                        // TODO - REMOVE the manual creation of the client ABSOLUTELY
                        connectedClients.add(new Client(login, this));
//                        connection();
                        System.out.println("CONNECTED CLIENTS : " + connectedClients);

                        if (IsConnect(login)) {
                            // TODO - remove debug comments here
                            System.out.println("logged in");
                            broadcast(msg);
                        }
                        // TODO - remove debug comments here
                        System.out.println("NOT logged in");
                    } else {
                        // TODO - remove debug comments here
                        System.out.println("in else");
                        // Test if server == leader
                        if (leader == null) {
                            // TODO - remove debug comments here
                            System.out.println("in if 2");
                            // Yes, send to connected server
                            connectedServer.forEach((key, value) -> {
                                if (!key.equals(name)) {
                                    value.queueMessage(packet);
                                }
                            });
                        } else {
                            // TODO - remove debug comments here
                            System.out.println("in else 2");
                            // No, send to leader
                            leader.queueMessage(packet);
                        }
                    }
                    logger.info(packet.components().toString());
                    publicMessageReader.reset();
                }
                case REFILL -> logger.info("REFILL");

                case ERROR -> {
                    logger.info("ERROR");
                    silentlyClose();
                }
            }
        }

        /**
         *
         */
        public void connection() {
            status = connectReader.process(bufferIn);
            switch (status) {
                case DONE -> {
                    logger.info("DONE");
                    var packet = connectReader.get();
                    var login = packet.components().get(0);
                    logger.info(login);
                    if (IsConnect(login)) {
                        var packetRefusal = new PacketString(3, new ArrayList<>());
                        queueMessage(packetRefusal);
                    } else {
                        connectedClients.add(new Client(login, this));
                        connectionAccepted(login);
                    }
                    connectReader.reset();
                }
                case REFILL -> logger.info("REFILL");

                case ERROR -> {
                    logger.info("ERROR");
                    silentlyClose();
                }
            }
        }

        /**
         * @param login String
         */
        private void connectionAccepted(String login) {
            var list = new ArrayList<String>();
            list.add(login);
            var packetAccepted = new PacketString(2, list);
            queueMessage(packetAccepted);
        }

        /**
         * Add a message to the message queue, tries to fill bufferOut and updateInterestOps
         *
         * @param packet Message
         */
        public void queueMessage(Packet packet) {
            // TODO - remove debug comments here
            System.out.println("in queueMessage");

            queue.add(packet);
            logger.info("" + queue.size());
            processOut();
            updateInterestOps();

            // TODO - remove debug comments here
            System.out.println("end of queueMessage");
        }

        /**
         * Try to fill bufferOut from the message queue
         */
        private void processOut() {
            // TODO - remove debug comments here
            System.out.println("in processOut");

            var previewMsg = queue.peek();
            while (!queue.isEmpty() && bufferOut.remaining() >= previewMsg.size()) {
                var fullMsg = queue.poll();
                if (fullMsg == null) return;

                bufferOut.put(fullMsg.parseToByteBuffer());
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
            // TODO - remove debug comments here
            System.out.println("in updateInterestOps");

            var newInterestOps = 0;
            if (bufferIn.hasRemaining() && !closed) newInterestOps = newInterestOps | SelectionKey.OP_READ;

            if (bufferOut.position() != 0) newInterestOps = newInterestOps | SelectionKey.OP_WRITE;

            if (newInterestOps == 0) silentlyClose();
            else key.interestOps(newInterestOps);
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
            processIn();
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
    }
}

