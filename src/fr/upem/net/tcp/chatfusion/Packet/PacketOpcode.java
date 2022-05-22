package fr.upem.net.tcp.chatfusion.Packet;

import java.nio.ByteBuffer;
import java.util.List;

public class PacketOpcode implements Packet {

    private final int opCode;

    public PacketOpcode(int opCode) {
        this.opCode = opCode;
    }

    @Override
    public int opCodeGet() {
        return opCode;
    }

    @Override
    public int size() {
        return Integer.BYTES;
    }

    @Override
    public List<String> components() {
        return null;
    }

    @Override
    public ByteBuffer parseToByteBuffer() {
        ByteBuffer bb = ByteBuffer.allocate(size());
        bb.putInt(opCode);
        return bb;
    }
}