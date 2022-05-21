package fr.upem.net.tcp.chatfusion;

import java.nio.charset.StandardCharsets;

public record Message (String login, String message){

    /**
     * Get size of a message composed of :
     *  - length of the message (int) : 4 bytes
     *  - message (String) : n bytes
     *  - length of the login name (int) : 4 bytes
     *  - login name (String) : n bytes
     * @return int
     */
    public int Size(){
        return StandardCharsets.UTF_8.encode(login).limit() + StandardCharsets.UTF_8.encode(message).limit() + 8;
    }
}
