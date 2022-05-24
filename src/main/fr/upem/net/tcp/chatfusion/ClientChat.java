package fr.upem.net.tcp.chatfusion;

import fr.upem.net.tcp.chatfusion.Packet.Message;
import fr.upem.net.tcp.chatfusion.Reader.MessageReader;
import fr.upem.net.tcp.chatfusion.Reader.Reader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
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
    private final ArrayDeque<Message> queueOut = new ArrayDeque<>(CAPACITY);

    public ClientChat(String login, InetSocketAddress serverAddress) throws IOException {
        // TODO - remove debug comments here
        System.out.println("Constructor");
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
        // TODO - remove debug comments here
        System.out.println("consoleRun");
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
        // TODO - remove debug comments here
        System.out.println("SendCommand");
        synchronized (lock) {
            while (queueOut.size() == CAPACITY) lock.wait();
            queueOut.add(new Message(login, msg));
            selector.wakeup();
        }
    }

    /**
     * Processes the command from the BlockingQueue
     */
    private void processCommands() {
        // TODO - remove debug comments here
        System.out.println("processCommand");
        synchronized (lock) {
            if (queueOut.isEmpty()) return;
            var message = (Message) queueOut.pop();
            uniqueContext.queueMessage(message);
            lock.notify();
        }
    }

    public void launch() throws IOException {
        // TODO - remove debug comments here
        System.out.println("launch");
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
        // TODO - remove debug comments here
        System.out.println("treatKey");
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
        // TODO - remove debug comments here
        System.out.println("silentlyClose");
        Channel sc = key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        // TODO - remove debug comments here
        System.out.println("main");
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
        private final ArrayDeque<Message> queue = new ArrayDeque<>();
        private boolean closed = false;
        private final Charset cs = StandardCharsets.UTF_8;
        private final MessageReader msgReader = new MessageReader();

        private Context(SelectionKey key) {
            // TODO - remove debug comments here
            System.out.println("Context.constructor");
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
            // TODO - remove debug comments here
            System.out.println("Context.processIn");
            for (; ; ) {
                Reader.ProcessStatus status = msgReader.process(bufferIn);
                switch (status) {
                    case DONE -> {
                        var value = msgReader.get();
                        System.out.println(value.login() + ": " + value.message());
                        msgReader.reset();
                    }
                    case REFILL -> {
                        updateInterestOps();
                        return;
                    }
                    case ERROR -> {
                        silentlyClose();
                        return;
                    }
                }
            }
        }

        /**
         * Add a message to the message queue, tries to fill bufferOut and updateInterestOps
         */
        private void queueMessage(Message msg) {
            // TODO - remove debug comments here
            System.out.println("Context.queueMessage");
            queue.add(msg);
            processOut();
            updateInterestOps();
        }

        /**
         * Try to fill bufferOut from the message queue
         */
        private void processOut() {
            // TODO - remove debug comments here
            System.out.println("Context.processOut");
            while (!queue.isEmpty()) {
                var val = queue.peek(); // take the value without removing it from the queue
                var bufferLogin = cs.encode(val.login());
                var bufferMessage = cs.encode(val.message());
                if (bufferOut.remaining() < (2 * Integer.BYTES + bufferLogin.remaining() + bufferMessage.remaining())) // if size of buffer too small
                    break;
                bufferOut.putInt(bufferLogin.remaining()).put(bufferLogin);
                bufferOut.putInt(bufferMessage.remaining()).put(bufferMessage);
                queue.pop(); // to remove the message from the queue (because peek() doesn't)
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
            // TODO - remove debug comments here
            System.out.println("Context.updateInterestOps");
            int ops = 0;

            if (!closed && bufferIn.hasRemaining()) ops |= SelectionKey.OP_READ;

            if (bufferOut.position() > 0) ops |= SelectionKey.OP_WRITE;

            if (ops == 0) silentlyClose();
            else key.interestOps(ops);
        }

        private void silentlyClose() {
            // TODO - remove debug comments here
            System.out.println("Context.silentlyClose");
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
            // TODO - remove debug comments here
            System.out.println("Context.doRead");
            if (sc.read(bufferIn) == -1) // read() returns -1 when connection closed
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
            // TODO - remove debug comments here
            System.out.println("Context.doWrite");
            sc.write(bufferOut.flip());
            bufferOut.compact();
            updateInterestOps();
        }

        public void doConnect() throws IOException {
            // TODO - remove debug comments here
            System.out.println("Context.doConnect");
            if (!sc.finishConnect()) {
                logger.warning("Bad thing happened");
                return;
            }
            updateInterestOps();
        }
    }

}
