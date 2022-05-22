package fr.upem.net.tcp.chatfusion.Component;


import java.nio.ByteBuffer;
import java.util.List;

public interface Packet {
    int opCodeGet();

    int size();

    List<String> components();

    ByteBuffer parseToByteBuffer();
}
