package fr.upem.net.tcp.chatfusion;

import fr.upem.net.tcp.chatfusion.Packet.Message;
import fr.upem.net.tcp.chatfusion.Packet.Packet;
import fr.upem.net.tcp.chatfusion.Packet.PacketFusionInit;
import fr.upem.net.tcp.chatfusion.Packet.PacketString;
import fr.upem.net.tcp.chatfusion.Reader.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    public ServerChatFusion(int port, String name) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        // switch server to non-blocking mode
        serverSocketChannel.configureBlocking(false);
        selector = Selector.open();
        this.name = name;
        // initialize by default the leader being the server itself
        this.leader = null;
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
        var keys = selector.keys();
        for (var key : keys) {
            var attach = key.attachment();
            if (attach == null) continue;
            var context = (Context) attach;
            context.queueMessage(packet);
        }
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

        /**
         * Process the content of bufferIn. <br>
         * The convention is that bufferIn is in write-mode
         * before the call to process and after the call
         */
        private void processIn() {
            for (; ; ) {
                logger.info("DONE");
                int opcode = bufferIn.getInt();
                switch (opcode) {
                    case 0, 1 -> connection();
                    case 4 -> publicMessage();
                    case 8 -> initFusion();
                }
            }
        }

        /**
         *
         */
        private void initFusion() {
            status = publicMessageReader.process(bufferIn);
            switch (status) {
                case DONE -> {
                    // get packet from Reader
                    var packet = fusionInitReaderOK.get();
                    // Test if server == leader
                    if (leader == null) {
                        if (!hasServerInCommon(packet.components())) {
                            var connectedServerName = getListConnectedServer();
                            try {
                                var socketAddress = sc.getLocalAddress();
                                var packetFusionInit = new PacketFusionInit(9, name, socketAddress, connectedServer.size(), connectedServerName);
                                queueMessage(packetFusionInit);
                            } catch (IOException e) {
                                logger.info("fail socketAddress");
                                return;
                            }

                            switchLeaderName(packet.GetName());

                            fusion(packet);
                        } else {

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
                // TODO - send packet 14
                silentlyClose();
            } else {
                connectedServer.put(packet.GetName(),this);
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
            status = publicMessageReader.process(bufferIn);
            switch (status) {
                case DONE -> {
                    // send buffer to all connected clients
                    var packet = publicMessageReader.get();
                    var nameServer = packet.components().get(0);
                    var login = packet.components().get(1);
                    var message = packet.components().get(2);
                    Message msg = new Message(login, message);
                    if (nameServer.equals(name)) {
                        if (IsConnect(login)) {
                            broadcast(msg);
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
            logger.info("DONE");
            var packet = connectReader.get();
            var login = packet.components().get(0);
            logger.info(login);
            if (IsConnect(login)) {
                var packetRefusal = new PacketString(3, new ArrayList<>());
                queueMessage(packetRefusal);
            } else {
                connectedClients.add(new Client(login));
                connectionAccepted(login);
            }
            connectReader.reset();
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
            queue.add(packet);
            logger.info("" + queue.size());
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

