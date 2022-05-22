package fr.upem.net.tcp.chatfusion.trame;


import java.nio.ByteBuffer;
import java.util.List;

public interface Trame {
    int OpCodeGet();
    int Size();
    List Compsants();
    ByteBuffer ParsetoByteBuffer();
}
