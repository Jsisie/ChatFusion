package fr.upem.net.tcp.chatfusion.trame;


import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class TrameString implements Trame{
    private int opCode;
    private List<String> composants;

    public TrameString(int opCode, List<String> list){
        this.opCode = opCode;
        this.composants = list;
    }
    @Override
    public int OpCodeGet() {
        return opCode;
    }

    @Override
    public int Size() {
        var size = Integer.BYTES;
        for (var composant: composants) {
            size += StandardCharsets.UTF_8.encode(composant).limit() + Integer.BYTES;
        }
        return size;
    }

    @Override
    public List<String> Compsants() {
        return composants;
    }

    @Override
    public ByteBuffer ParsetoByteBuffer() {
        ByteBuffer bb = ByteBuffer.allocate(size());
        bb.putInt(opCode);
        for (var composant: composants) {
            var bbComposant = StandardCharsets.UTF_8.encode(composant);
            bb.putInt(bbComposant.limit());
            bb.put(bbComposant);
        }
        return bb;
    }
}
