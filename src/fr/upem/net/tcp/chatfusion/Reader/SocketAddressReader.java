package fr.upem.net.tcp.chatfusion.Reader;

import java.net.SocketAddress;
import java.nio.ByteBuffer;

public class SocketAddressReader implements Reader<SocketAddress> {

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        return null;
    }

    @Override
    public SocketAddress get() {
        return null;
    }

    @Override
    public void reset() {

    }
}
