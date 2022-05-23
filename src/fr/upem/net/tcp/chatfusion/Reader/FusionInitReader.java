package fr.upem.net.tcp.chatfusion.Reader;

import fr.upem.net.tcp.chatfusion.Packet.PacketFusionInit;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class FusionInitReader implements Reader {

    private enum State {DONE, WAITING, ERROR}
    private FusionInitReader.State state = FusionInitReader.State.WAITING;
    private PacketFusionInit value;
    private String name = "";
    private int nbMembre = -1;
    private List<String> namesMember = new ArrayList<>();
    private final StringReader stringReader = new StringReader();
    private final IntReader intReader = new IntReader();
    //private SocketAdresse adresse;



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
//todo reup nbmembre
        if(nbMembre == -1){
            ProcessStatus status = intReader.process(bb);
            switch (status) {
                case DONE:
                    nbMembre = intReader.get();
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
        while (namesMember.size() < nbMembre) {
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
        value = new PacketFusionInit(8, name, nbMembre, namesMember);
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
        nbMembre = -1;
        namesMember.clear();
    }
}
