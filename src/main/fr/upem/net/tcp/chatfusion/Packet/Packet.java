package fr.upem.net.tcp.chatfusion.Packet;


import java.nio.ByteBuffer;
import java.util.List;

public interface Packet {

    int opCodeGet();

    int size();

    List<?> components();

    ByteBuffer generateByteBuffer();
}
