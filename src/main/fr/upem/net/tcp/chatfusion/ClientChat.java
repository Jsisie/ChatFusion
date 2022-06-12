package fr.upem.net.tcp.chatfusion;

import fr.upem.net.tcp.chatfusion.Packet.Packet;
import fr.upem.net.tcp.chatfusion.Packet.PacketString;
import fr.upem.net.tcp.chatfusion.Reader.PacketReader;
import fr.upem.net.tcp.chatfusion.Reader.Reader.ProcessStatus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Logger;

public class ClientChat {

    static private final int BUFFER_SIZE = 10_000;
    static private final Logger logger = Logger.getLogger(ClientChat.class.getName());
    private final SocketChannel sc;
    private final Selector selector;
    private final InetSocketAddress serverAddress;
    private final String login;
    private final Thread console;
    private Context uniqueContext;
    private final Object lock = new Object();
    private final int CAPACITY = 10;
    private final ArrayDeque<Packet> queueOut = new ArrayDeque<>(CAPACITY);

    public ClientChat(String login, InetSocketAddress serverAddress) throws IOException {
        this.serverAddress = serverAddress;
        this.login = login;
        this.sc = SocketChannel.open();
        this.selector = Selector.open();
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
     */
    private void sendCommand(String msg) throws InterruptedException {
        synchronized (console) {
            if ("LOGIN".equals(msg)) {
                while (queueOut.size() == CAPACITY) lock.wait();
                var packet = new PacketString(0, login);
                queueOut.add(packet);
                selector.wakeup();
            } else {
                if (uniqueContext.nameServer != null) {
                    while (queueOut.size() == CAPACITY) lock.wait();
                    var packet = new PacketString(4, List.of(uniqueContext.nameServer, login, msg));
                    queueOut.add(packet);
                    selector.wakeup();
                } else {
                    logger.info("Client is not connected yet to a server");
                }
            }
        }
    }

    /**
     * Processes the command from the BlockingQueue
     */
    private void processCommands() {
        synchronized (lock) {
            if (queueOut.isEmpty()) return;
            var packet = (Packet) queueOut.pop();
            uniqueContext.queueMessage(packet);
            lock.notify();
        }
    }

    public void launch() throws IOException {
        sc.configureBlocking(false);
        var key = sc.register(selector, SelectionKey.OP_CONNECT);
        uniqueContext = new Context(key);
        key.attach(uniqueContext);
        sc.connect(serverAddress);
        console.start();

        while (!Thread.interrupted()) {
            try {
                selector.select(this::treatKey);
                processCommands();
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
        }
    }

    private void treatKey(SelectionKey key) {
        try {
            if (key.isValid() && key.isConnectable()) {
                uniqueContext.doConnect();
            }
            if (key.isValid() && key.isWritable()) {
                uniqueContext.doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                uniqueContext.doRead();
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    private void silentlyClose(SelectionKey key) {
        Channel sc = key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 3) {
            usage();
            return;
        }
        new ClientChat(args[0], new InetSocketAddress(args[1], Integer.parseInt(args[2]))).launch();
    }

    private static void usage() {
        System.out.println("Usage : ClientChat login hostname port");
    }


    // #################### CONTEXT #################### //

    private static class Context {
        private final SelectionKey key;
        private final SocketChannel sc;
        private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
        private final PacketReader packetReader = new PacketReader();
        private final ArrayDeque<Packet> queue = new ArrayDeque<>();
        private boolean closed = false;
        private Packet packet;
        private String nameServer;

        private Context(SelectionKey key) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
        }

        /**
         * Process the content of bufferIn
         * <p>
         * The convention is that bufferIn is in write-mode before the call to process
         * and after the call
         */
        private void processIn() {
            for (; ; ) {
                ProcessStatus status = packetReader.process(bufferIn);
                switch (status) {
                    case DONE -> {
                        packet = packetReader.get();
                        switch (packet.opCodeGet()) {
                            case 2 -> {
                                nameServer = (String) packet.components().get(0);
                                packetReader.reset();
                                logger.info("Client successfully connected to server");
                                return;
                            }
                            case 3 -> {
                                logger.info("Client couldn't connect to the server");
                                packetReader.reset();
                                silentlyClose();
                                return;
                            }
                            // public message
                            case 4 -> {
                                publicMessage();
                                packetReader.reset();
                                return;
                            }
                        }
                        packetReader.reset();
                    }
                    case REFILL -> {
                        logger.info("REFILL");
                        return;
                    }
                    case ERROR -> {
                        logger.info("ERROR");
                        silentlyClose();
                        return;
                    }
                }
            }
        }

        private void publicMessage() {
            var nameServer = packet.components().get(0);
            String login = (String) packet.components().get(1);
            var message = packet.components().get(2);

            System.out.println(login + "[" + nameServer + "]: " + message);
        }

        /**
         * Add a message to the message queue, tries to fill bufferOut and updateInterestOps
         */
        private void queueMessage(Packet packet) {
            queue.add(packet);
            processOut();
            updateInterestOps();
        }

        /**
         * Try to fill bufferOut from the message queue
         */
        private void processOut() {
            var previewMsg = queue.peek();
            while (!queue.isEmpty() && bufferOut.remaining() >= previewMsg.size()) {// take the value without removing it from the queue
                var fullMsg = queue.poll();
                if (fullMsg == null) return;
                bufferOut.put(fullMsg.generateByteBuffer().flip());
            }
        }

        /**
         * Update the interestOps of the key looking only at values of the boolean
         * closed and of both ByteBuffers.
         * <p>
         * The convention is that both buffers are in write-mode before the call to
         * updateInterestOps and after the call. Also, it is assumed that process has
         * been called just before updateInterestOps.
         */
        private void updateInterestOps() {
            int ops = 0;

            if (!closed && bufferIn.hasRemaining())
                ops |= SelectionKey.OP_READ;

            if (bufferOut.position() > 0)
                ops |= SelectionKey.OP_WRITE;

            if (ops == 0)
                silentlyClose();
            else
                key.interestOps(ops);
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
            if (sc.read(bufferIn) == -1)
                closed = true;
            processIn();
        }

        /**
         * Performs the write action on sc
         * <p>
         * The convention is that both buffers are in write-mode before the call to
         * doWrite and after the call
         *
         * @throws IOException Is thrown if the SocketChannel <b>sc</b> is closed while reading from it
         */
        private void doWrite() throws IOException {
            sc.write(bufferOut.flip());
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
