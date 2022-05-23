package fr.upem.net.tcp.chatfusion.Reader;

import fr.upem.net.tcp.chatfusion.Packet.PacketFusionInit;

import java.net.InetSocketAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class FusionInitReader implements Reader<PacketFusionInit> {

    private enum State {DONE, WAITING, ERROR}

    private FusionInitReader.State state = FusionInitReader.State.WAITING;
    private PacketFusionInit value;
    private int opCode;
    private String name = "";
    private InetSocketAddress sc;
    private int nbMember = -1;
    private final List<String> namesMember = new ArrayList<>();
    private final StringReader stringReader = new StringReader();
    private final IntReader intReader = new IntReader();
    private final SocketAddressReader socketAddressReader = new SocketAddressReader();


    public FusionInitReader(int opCode) {
        this.opCode = opCode;
    }

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == FusionInitReader.State.DONE || state == FusionInitReader.State.ERROR) {
            throw new IllegalStateException();
        }

        if (name.isEmpty()) {
            ProcessStatus status = stringReader.process(bb);
            switch (status) {
                case DONE:
                    name = stringReader.get();
                    break;
                case REFILL:
                    return ProcessStatus.REFILL;
                case ERROR:
                    state = FusionInitReader.State.ERROR;
                    return ProcessStatus.ERROR;
            }
            stringReader.reset();
        }
        // TODO - get Socket Address
        if (sc == null) {
            ProcessStatus status = socketAddressReader.process(bb);
            switch (status) {
                case DONE:
                    sc = (InetSocketAddress) socketAddressReader.get();
                    break;
                case REFILL:
                    return ProcessStatus.REFILL;
                case ERROR:
                    state = FusionInitReader.State.ERROR;
                    return ProcessStatus.ERROR;
            }
            socketAddressReader.reset();
        }

        //todo reup nbmembre
        if (nbMember == -1) {
            ProcessStatus status = intReader.process(bb);
            switch (status) {
                case DONE:
                    nbMember = intReader.get();
                    break;
                case REFILL:
                    return ProcessStatus.REFILL;
                case ERROR:
                    state = FusionInitReader.State.ERROR;
                    return ProcessStatus.ERROR;
            }
            intReader.reset();
        }
//todo modifier en for pour recuperer les names de serveurs
        while (namesMember.size() < nbMember) {
            ProcessStatus status = stringReader.process(bb);
            switch (status) {
                case DONE:
                    var nameMember = stringReader.get();
                    namesMember.add(nameMember);
                    break;
                case REFILL:
                    return ProcessStatus.REFILL;
                case ERROR:
                    state = FusionInitReader.State.ERROR;
                    return ProcessStatus.ERROR;
            }
            stringReader.reset();
        }
        state = FusionInitReader.State.DONE;
        value = new PacketFusionInit(opCode, name, nbMember, namesMember);
        //stringReader.reset();
        return ProcessStatus.DONE;
    }

    @Override
    public PacketFusionInit get() {
        if (state != FusionInitReader.State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        name = "";
        nbMember = -1;
        namesMember.clear();
    }
}
