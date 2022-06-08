package fr.upem.net.tcp.chatfusion.Packet;


import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;


public record PacketString(int opCode, List<String> components) implements Packet {

    private static final Charset cs = StandardCharsets.UTF_8;

    public PacketString(int opCode, String list) {
        this(opCode, List.of(list));
    }

    @Override
    public int opCodeGet() {
        return opCode;
    }

    @Override
    public int size() {
        var size = Integer.BYTES;
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
        for (var component : components) {
            var bbComponent = cs.encode(component);
            bb.putInt(bbComponent.limit());
            bb.put(bbComponent);
        }
        return bb;
    }
}
