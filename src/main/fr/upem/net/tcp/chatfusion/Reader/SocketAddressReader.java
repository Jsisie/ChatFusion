package fr.upem.net.tcp.chatfusion.Reader;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.StringJoiner;

public class SocketAddressReader implements Reader<SocketAddress> {
    private enum State {DONE, WAITING, ERROR}

    private int ipv;
    private StringJoiner sj = new StringJoiner(".");
    private int port;
    private InetSocketAddress value;
    private final IntReader intReader = new IntReader();
    private State state = State.WAITING;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == SocketAddressReader.State.DONE || state == SocketAddressReader.State.ERROR) {
            throw new IllegalStateException();
        }
        var intState = intReader.process(bb);

        if (intState.equals(ProcessStatus.DONE)) {
            ipv = intReader.get();
        } else if (intState.equals(ProcessStatus.ERROR)) {
            state = State.ERROR;
        }
        intReader.reset();

        for (int i = 0; i < ipv; i++) {
            var b = (bb.get() & 0xff);
            sj.add(Integer.toString(b));
        }
        intState = intReader.process(bb);

        if (intState.equals(ProcessStatus.DONE)) {
            port = intReader.get();
        } else if (intState.equals(ProcessStatus.ERROR)) {
            state = State.ERROR;
        }

        state = State.DONE;
        value = new InetSocketAddress(sj.toString(), port);
        intReader.reset();
        return ProcessStatus.DONE;

    }

    @Override
    public SocketAddress get() {
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING;
        sj = new StringJoiner(".");
    }
}
