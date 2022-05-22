package fr.upem.net.tcp.chatfusion.Packet;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.List;

public class PacketSocketAddress implements Packet {

    private final int opCode;

    private final SocketAddress sa;

    public PacketSocketAddress(int opCode, SocketAddress sa) {
        this.opCode = opCode;
        this.sa = sa;
    }

    @Override
    public int opCodeGet() {
        return 0;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public List<?> components() {
        return null;
    }

    @Override
    public ByteBuffer parseToByteBuffer() {
        return null;
    }
}
