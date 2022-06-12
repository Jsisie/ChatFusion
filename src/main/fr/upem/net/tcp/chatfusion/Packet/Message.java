package fr.upem.net.tcp.chatfusion.Packet;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public record Message(String login, String message) implements Packet {

    private static final Charset cs = StandardCharsets.UTF_8;

    /**
     * Get size of a message composed of : <br>
     * - length of the message (int) : 4 bytes <br>
     * - message (String) : n bytes <br>
     * - length of the login name (int) : 4 bytes <br>
     * - login name (String) : n bytes <br>
     *
     * @return int
     */

    @Override
    public int opCodeGet() {
        return -1;
    }

    @Override
    public int size() {
        return cs.encode(login).limit() + cs.encode(message).limit() + Integer.BYTES * 3;
    }

    @Override
    public List<?> components() {
        return null;
    }

    @Override
    public ByteBuffer generateByteBuffer() {
        var bbLogin = cs.encode(login);
        var bbMessage = cs.encode(message);
        return ByteBuffer.allocate(size()).putInt(4).putInt(bbLogin.limit()).put(bbLogin).putInt(bbMessage.limit()).put(bbMessage);
    }
}
