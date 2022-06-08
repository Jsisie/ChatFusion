package fr.upem.net.tcp.chatfusion.Reader;

import fr.upem.net.tcp.chatfusion.Packet.Packet;
import fr.upem.net.tcp.chatfusion.Packet.PacketSocketAddress;

import java.nio.ByteBuffer;

public class PacketReader implements Reader<Packet> {
    private enum State {DONE, WAITING, ERROR}

    private State state = State.WAITING;
    private final IntReader intReader = new IntReader();
    private final ConnectReader connectReader = new ConnectReader();
    private final PublicMessageReader publicMessageReader = new PublicMessageReader();
    private final FusionInitReader fusionInitReader = new FusionInitReader(8);
    private final SocketAddressReader socketAddressReader = new SocketAddressReader();
    private int opCode;
    private Packet packet;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        ProcessStatus status = intReader.process(bb);
        switch (status) {
            case DONE -> opCode = intReader.get();
            case REFILL -> {
                return ProcessStatus.REFILL;
            }
            case ERROR -> {
                state = State.ERROR;
                return ProcessStatus.ERROR;
            }
        }
        intReader.reset();

        switch (opCode) {
            case 0, 1 -> {
                status = connectReader.process(bb);
                switch (status) {
                    case DONE -> packet = connectReader.get();
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case ERROR -> {
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                }
                connectReader.reset();
            }
            case 4 -> {
                status = publicMessageReader.process(bb);
                switch (status) {
                    case DONE -> packet = publicMessageReader.get();
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case ERROR -> {
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                }
                publicMessageReader.reset();
            }
            case 8 -> {
                status = fusionInitReader.process(bb);
                switch (status) {
                    case DONE:
                        // get packet from Reader
                        packet = fusionInitReader.get();
                    case REFILL:
                        return ProcessStatus.REFILL;
                    case ERROR:
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                }
                fusionInitReader.reset();
            }
            case 14 -> {
                status = socketAddressReader.process(bb);
                switch (status) {
                    case DONE:
                        var sa = socketAddressReader.get();
                        packet = new PacketSocketAddress(14, sa);
                    case REFILL:
                        return ProcessStatus.REFILL;
                    case ERROR:
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                }
                socketAddressReader.reset();
            }
        }
        return ProcessStatus.DONE;
    }

    @Override
    public Packet get() {
        return packet;
    }

    @Override
    public void reset() {
        state = State.WAITING;
    }
}
