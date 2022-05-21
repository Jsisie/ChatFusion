package fr.upem.net.tcp.chatfusion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerChaton {
    private class Context {
        private final SelectionKey key;
        private final SocketChannel sc;
        private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
        private final ArrayDeque<Message> queue = new ArrayDeque<>();
        private Charset cs = Charset.forName("UTF-8");
        private MessageReader msgReader = new MessageReader();
        private StringReader stringReader = new StringReader();
        private final ServerChaton server; // we could also have Context as an instance class, which would naturally
        // give access to ServerChatInt.this
        private boolean closed = false;

        private Context(ServerChaton server, SelectionKey key) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            this.server = server;
        }

        /**
         * Process the content of bufferIn
         * <p>
         * The convention is that bufferIn is in write-mode before the call to process and
         * after the call
         */
        private void processIn() {
            for (; ; ) {
                int opcode = bufferIn.getInt();
                switch (opcode) {
                    case (0 | 1):
                        Connection();
                }
                Reader.ProcessStatus status = msgReader.process(bufferIn);
                switch (status) {
                    case DONE:
                        logger.info("DONE");
                        var value = msgReader.get();
                        logger.info(value.toString());
                        server.broadcast(value);
                        msgReader.reset();
                        break;
                    case REFILL:
                        logger.info("REFILL");
                        return;

                    case ERROR:
                        logger.info("ERROR");
                        silentlyClose();
                        return;
                }
            }
        }

        public void Connection() {
            Reader.ProcessStatus status = stringReader.process(bufferIn);
            switch (status) {
                case DONE:
                    logger.info("DONE");
                    var login = stringReader.get();
                    logger.info(login);

                    if (IsConnect(login)) {
                        if (bufferOut.remaining() >= Integer.BYTES) {
                            bufferOut.putInt(3);
                            updateInterestOps();
                        }
                    } else {
                        Client client = new Client(login);
                        Clients.add(client);
                        ConnectionAccepted(login);
                    }


                    msgReader.reset();
                    break;
                case REFILL:
                    logger.info("REFILL");
                    return;

                case ERROR:
                    logger.info("ERROR");
                    silentlyClose();
                    return;
            }
        }

        private void ConnectionAccepted(String login) {
            var bb = StandardCharsets.UTF_8.encode(login);
            if (bufferOut.remaining() >= Integer.BYTES + bb.limit()) {
                bufferOut.putInt(2);
                bufferOut.put(bb);
                updateInterestOps();
            }
        }

        /**
         * Add a message to the message queue, tries to fill bufferOut and updateInterestOps
         *
         * @param msg
         */
        public void queueMessage(Message msg) {
            queue.add(msg);
            logger.info("" + queue.size());
            processOut();
            updateInterestOps();
        }

        /**
         * Try to fill bufferOut from the message queue
         */
        private void processOut() {
            var previewMsg = queue.peek();
            while (!queue.isEmpty() && bufferOut.remaining() >= previewMsg.Size()/*previewMsg.login().length() + previewMsg.message().length() + 2 * Integer.BYTES*/) {
                var fullMsg = queue.poll();
                var login = fullMsg.login();
                var msg = fullMsg.message();
                bufferOut.putInt(login.length()).put(cs.encode(login)).putInt(msg.length()).put(cs.encode(msg));

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
            var newInterestOps = 0;
            if (bufferIn.hasRemaining() && !closed)
                newInterestOps = newInterestOps | SelectionKey.OP_READ;

            if (bufferOut.position() != 0)
                newInterestOps = newInterestOps | SelectionKey.OP_WRITE;

            if (newInterestOps == 0)
                silentlyClose();
            else
                key.interestOps(newInterestOps);
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
         * @throws IOException
         */
        private void doRead() throws IOException {
            if (sc.read(bufferIn) == -1)
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
         * @throws IOException
         */
        private void doWrite() throws IOException {
            bufferOut.flip();
            sc.write(bufferOut);
            bufferOut.compact();
            processOut();
            updateInterestOps();
        }

    }

    private boolean IsConnect(String login) {
        for (var client : Clients) {
            if (client.checkIsLogin(login))
                return true;
        }
        return false;
    }

    private record Client(String login) {
        private boolean checkIsLogin(String login) {
            return this.login.equals(login);
        }
    }

    private List<Client> Clients = new ArrayList<>();
    private static final int BUFFER_SIZE = 1_024;
    private static final Logger logger = Logger.getLogger(ServerChaton.class.getName());

    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;

    private ServerSocketChannel leader;
    private String name;

    public ServerChaton(int port, String name) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
        this.name = name;
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
        Channel sc = (Channel) key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    /**
     * Add a message to all connected clients queue
     *
     * @param msg
     */
    private void broadcast(Message msg) {
        var keys = selector.keys();
        for (var key : keys) {
            var attach = key.attachment();
            if (attach == null) continue;
            var context = (Context) attach;
            context.queueMessage(msg);
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 2) {
            usage();
            return;
        }
        new ServerChaton(Integer.parseInt(args[0]), args[1]).launch();
    }

    private static void usage() {
        System.out.println("Usage : ServerSumBetter port");
    }

}