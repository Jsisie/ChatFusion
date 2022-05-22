package fr.upem.net.tcp.chatfusion;

import java.nio.charset.StandardCharsets;

public record Message(String login, String message) {

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
        return StandardCharsets.UTF_8.encode(login).limit() + StandardCharsets.UTF_8.encode(message).limit() + Integer.BYTES * 2;
    }
}
