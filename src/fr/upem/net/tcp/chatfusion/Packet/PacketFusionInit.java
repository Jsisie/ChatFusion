package fr.upem.net.tcp.chatfusion.Packet;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class PacketFusionInit implements Packet {
    private final int opCode;
    private final List<String> components;
    private final String name;
    private final int nbMembers;
    private  final SocketAddress sa;
    private final Charset cs = StandardCharsets.UTF_8;

    public PacketFusionInit(int opCode, String name, SocketAddress sa, int nbMembers, List<String> members) {
        this.opCode = opCode;
        this.name = name;
        this.nbMembers = nbMembers;
        this.components = members;
        this.sa = sa;
    }

    public String GetName(){
        return  name;
    }

    public SocketAddress getSocketAddress() {
        return sa;
    }

    @Override
    public int opCodeGet() {
        return opCode;
    }

    @Override
    public int size() {
        var size = Integer.BYTES * 2;
        size += cs.encode(name).limit() + Integer.BYTES;
        size += Integer.BYTES * 2 + Byte.BYTES * 4;
        for (var component : components)
            size += cs.encode(component).limit() + Integer.BYTES;
        return size;
    }

    @Override
    public List<String> components() {
        return components;
    }

    @Override
    public ByteBuffer parseToByteBuffer() {
        ByteBuffer bb = ByteBuffer.allocate(size());
        bb.putInt(opCode);
        var bbName = cs.encode(name);
        bb.putInt(bbName.limit());
        bb.put(bbName);

        var inetSA = (InetSocketAddress) sa;
        var bbIPAddress = cs.encode(inetSA.getHostName());
        bb.put(bbIPAddress);
        bb.putInt(inetSA.getPort());

        bb.putInt(name.length());
        bb.putInt(nbMembers);
        for (var component : components) {
            var bbComponent = cs.encode(component);
            bb.putInt(bbComponent.limit());
            bb.put(bbComponent);
        }
        return bb;
    }
}
