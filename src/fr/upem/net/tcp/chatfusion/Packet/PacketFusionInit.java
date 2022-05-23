package fr.upem.net.tcp.chatfusion.Packet;

import fr.upem.net.tcp.chatfusion.ServerChatOn;

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

    private final Charset cs = StandardCharsets.UTF_8;

    public PacketFusionInit(int opCode, String name, int nbMembers, List<String> members) {
        this.opCode = opCode;
        this.name = name;
        this.nbMembers = nbMembers;
        this.components = members;
    }

    public String GetName(){
        return  name;
    }

    // TODO - add sc as parameters ???
    public SocketAddress getSocketAddress() {
        return sc;
    }

    @Override
    public int opCodeGet() {
        return opCode;
    }

    @Override
    public int size() {
        var size = Integer.BYTES * 2;
        size += cs.encode(name).limit() + Integer.BYTES;
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
