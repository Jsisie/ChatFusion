package fr.upem.net.tcp.chatfusion;

import fr.upem.net.tcp.chatfusion.Packet.Packet;
import fr.upem.net.tcp.chatfusion.Packet.PacketString;
import fr.upem.net.tcp.chatfusion.Reader.MessageReader;
import fr.upem.net.tcp.chatfusion.Reader.PacketReader;
import  fr.upem.net.tcp.chatfusion.Reader.Reader.ProcessStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
//        System.out.println("Constructor");
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
//        System.out.println("consoleRun");
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
//        System.out.println("SendCommand");
        synchronized (console) {
            String[] cmd = msg.split(" ");
            switch (cmd[0]) {
                case "LOGIN" -> {
                    while (queueOut.size() == CAPACITY) lock.wait();
                    var packet = new PacketString(0, cmd[1]);
                    queueOut.add(packet);
                    selector.wakeup();
                }
                case "MESSAGE" -> {
                    while (queueOut.size() == CAPACITY) lock.wait();
                    var packet = new PacketString(4, List.of(cmd[1], cmd[2], cmd[3]));
                    queueOut.add(packet);
                    selector.wakeup();
                }
                default -> System.out.println("Unknown command typed");
            }
        }
    }

    /**
     * Processes the command from the BlockingQueue
     */
    private void processCommands() {
//        System.out.println("processCommand");
        synchronized (lock) {
            if (queueOut.isEmpty()) return;
            var packet = (Packet) queueOut.pop();
            uniqueContext.queueMessage(packet);
            lock.notify();
        }
    }

    public void launch() throws IOException {
//        System.out.println("launch");
        sc.configureBlocking(false);
        var key = sc.register(selector, SelectionKey.OP_CONNECT);
        uniqueContext = new Context(key);
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
//        System.out.println("treatKey");
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
            // lambda call in select requires to tunnel IOException
            throw new UncheckedIOException(ioe);
        }
    }

    private void silentlyClose(SelectionKey key) {
//        System.out.println("silentlyClose");
        Channel sc = key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
//        System.out.println("main");
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
        private final Charset cs = StandardCharsets.UTF_8;
        private final MessageReader msgReader = new MessageReader();
        private ProcessStatus status;
        private Packet packet;

        private Context(SelectionKey key) {
//            System.out.println("Context.constructor");
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
//            System.out.println("Context.processIn");
            for (; ; ) {
                status = packetReader.process(bufferIn);
                switch (status) {
                    case DONE -> {
                        packet = packetReader.get();
                        switch (packet.opCodeGet()) {
                            // reponse from server - connection
                            case 2 -> {
                                // TODO get the servername
                                packetReader.reset();
                                return;
                            }
                            case 3 ->{
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
//            System.out.println("Context.queueMessage");
            queue.add(packet);
            processOut();
            updateInterestOps();
        }

        /**
         * Try to fill bufferOut from the message queue
         */
        private void processOut() {
//            System.out.println("Context.processOut");
            var previewMsg = queue.peek();
            while (!queue.isEmpty() && bufferOut.remaining() >= previewMsg.size()) {// take the value without removing it from the queue
                var fullMsg = queue.poll();
                if (fullMsg == null) return;
                bufferOut.put(fullMsg.parseToByteBuffer().flip());
            }
        }

        /**
         * Update the interestOps of the key looking only at values of the boolean
         * closed and of both ByteBuffers.
         * <p>
         * The convention is that both buffers are in write-mode before the call to
         * updateInterestOps and after the call. Also it is assumed that process has
         * been be called just before updateInterestOps.
         */
        private void updateInterestOps() {
//            System.out.println("Context.updateInterestOps");
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
//            System.out.println("Context.silentlyClose");
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
//            System.out.println("Context.doRead");
            if (sc.read(bufferIn) == -1) // read() returns -1 when connection closed
                closed = true;
            processIn();
            updateInterestOps();
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
//            System.out.println("Context.doWrite");
            sc.write(bufferOut.flip());
            bufferOut.compact();
            processOut();
            updateInterestOps();
        }

        public void doConnect() throws IOException {
//            System.out.println("Context.doConnect");
            if (!sc.finishConnect()) {
                logger.warning("Bad thing happened");
                return;
            }
            updateInterestOps();
        }
    }

}
