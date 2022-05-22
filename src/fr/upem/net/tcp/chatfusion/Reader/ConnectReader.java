package fr.upem.net.tcp.chatfusion.Reader;

import fr.upem.net.tcp.chatfusion.Component.packetString;

import java.nio.ByteBuffer;
import java.util.*;

public class ConnectReader implements Reader<packetString> {
    private enum State {DONE, WAITING, ERROR}

    private ConnectReader.State state = ConnectReader.State.WAITING;
    private packetString packet;
    private String login = "";
    private final StringReader stringReader = new StringReader();

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == ConnectReader.State.DONE || state == ConnectReader.State.ERROR) {
            throw new IllegalStateException();
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
                    state = ConnectReader.State.ERROR;
                    return ProcessStatus.ERROR;
            }
            stringReader.reset();
        }


        state = ConnectReader.State.DONE;
        var list = new ArrayList<String>();
        list.add(login);
        packet = new packetString(0, list);
        return ProcessStatus.DONE;
    }

    @Override
    public packetString get() {
        if (state != ConnectReader.State.DONE) {
            throw new IllegalStateException();
        }
        return packet;
    }

    public void reset() {
        state = ConnectReader.State.WAITING;
        stringReader.reset();
        login = "";
    }
}
