package fr.upem.net.tcp.chatfusion.Packet;

import java.nio.ByteBuffer;
import java.util.List;

public record PacketOpcode (int opCode) implements Packet {

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
    public ByteBuffer generateByteBuffer() {
        ByteBuffer bb = ByteBuffer.allocate(size());
        bb.putInt(opCode);
        return bb;
    }
}
