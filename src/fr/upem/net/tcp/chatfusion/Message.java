package fr.upem.net.tcp.chatfusion;

public record Message (String login, String message){

    /**
     * Get size of a message composed of :
     *  - length of the message (int) : 4 bytes
     *  - message (String) : n bytes
     *  - length of the login name (int) : 4 bytes
     *  - login name (String) : n bytes
     * @return int
     */
    public int getSize() {
        return login.length() + message.length() + Integer.BYTES * 2;
    }
}
