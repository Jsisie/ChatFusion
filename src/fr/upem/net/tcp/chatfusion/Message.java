package fr.upem.net.tcp.chatfusion;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public record Message(String login, String message) {

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
    public int getSize() {
        return cs.encode(login).limit() + cs.encode(message).limit() + Integer.BYTES * 2;
    }
}
