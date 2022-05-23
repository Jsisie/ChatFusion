package fr.upem.net.tcp.chatfusion.Packet;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class PacketSocketAddress implements Packet {

    private final int opCode;
    private final SocketAddress sa;
    private final Charset cs = StandardCharsets.UTF_8;

    public PacketSocketAddress(int opCode, SocketAddress sa) {
        this.opCode = opCode;
        this.sa = sa;
    }

    @Override
    public int opCodeGet() {
        return opCode;
    }

    @Override
    public int size() {
        int size = Integer.BYTES;
        size += Integer.BYTES * 2 + Byte.BYTES * 4;
        return size;
    }

    @Override
    public List<SocketAddress> components() {
        return List.of(sa);
    }

    @Override
    public ByteBuffer parseToByteBuffer() {
        var bb = ByteBuffer.allocate(size());
        bb.putInt(opCode);
        var inetSA = (InetSocketAddress) sa;
        var bbIPAddress = cs.encode(inetSA.getHostName());
        bb.put(bbIPAddress);
        bb.putInt(inetSA.getPort());
        return bb;
    }
}
