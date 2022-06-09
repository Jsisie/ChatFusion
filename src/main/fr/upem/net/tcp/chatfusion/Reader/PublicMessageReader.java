package fr.upem.net.tcp.chatfusion.Reader;

import fr.upem.net.tcp.chatfusion.Packet.PacketString;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class PublicMessageReader implements Reader<PacketString> {
    private enum State {DONE, WAITING, ERROR}
    private PublicMessageReader.State state = PublicMessageReader.State.WAITING;
    private PacketString packet;
    private String server = "";
    private String login = "";
    private String msg = "";
    private final StringReader stringReader = new StringReader();

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        if (server.isEmpty()) {
            ProcessStatus status = stringReader.process(bb);
            switch (status) {
                case DONE:
                    server = stringReader.get();
                    break;
                case REFILL:
                    return ProcessStatus.REFILL;
                case ERROR:
                    state = PublicMessageReader.State.ERROR;
                    return ProcessStatus.ERROR;
            }
            stringReader.reset();
        }
        if (login.isEmpty()) {
            ProcessStatus status = stringReader.process(bb);
            switch (status) {
                case DONE:
                    login = stringReader.get();
                    break;
                case REFILL:
                    return ProcessStatus.REFILL;
                case ERROR:
                    state = PublicMessageReader.State.ERROR;
                    return ProcessStatus.ERROR;
            }
            stringReader.reset();
        }
        if (msg.isEmpty()) {
            ProcessStatus status = stringReader.process(bb);
            switch (status) {
                case DONE:
                    msg = stringReader.get();
                    break;
                case REFILL:
                    return ProcessStatus.REFILL;
                case ERROR:
                    state = PublicMessageReader.State.ERROR;
                    return ProcessStatus.ERROR;
            }
        }


        state = PublicMessageReader.State.DONE;
        var list = new ArrayList<String>();
        list.add(server);
        list.add(login);
        list.add(msg);
        packet = new PacketString(4, list);
        return ProcessStatus.DONE;
    }

    @Override
    public PacketString get() {
        if (state != PublicMessageReader.State.DONE) {
            throw new IllegalStateException();
        }
        System.out.println("packet = " + packet);
        return packet;
    }

    public void reset() {
        state = PublicMessageReader.State.WAITING;
        stringReader.reset();
        server = "";
        login = "";
        msg = "";
    }
}
