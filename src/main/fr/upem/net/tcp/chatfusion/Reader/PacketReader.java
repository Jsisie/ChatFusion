package fr.upem.net.tcp.chatfusion.Reader;

import fr.upem.net.tcp.chatfusion.Packet.Packet;
import fr.upem.net.tcp.chatfusion.Packet.PacketSocketAddress;

import java.net.SocketAddress;
import java.nio.ByteBuffer;

public class PacketReader implements Reader {
    private enum State {DONE, WAITING, ERROR}

    private State state = State.WAITING;
    private IntReader intReader = new IntReader();
    private ConnectReader connectReader = new ConnectReader();
    private PublicMessageReader publicMessageReader = new PublicMessageReader();
    private  FusionInitReader fusionInitReader = new FusionInitReader(8);
    private SocketAddressReader socketAddressReader = new SocketAddressReader();
    private int opCode;
    private Packet packet;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        ProcessStatus status = intReader.process(bb);
        switch (status) {
            case DONE:
                opCode = intReader.get();
                break;
            case REFILL:
                return ProcessStatus.REFILL;
            case ERROR:
                state = State.ERROR;
                return ProcessStatus.ERROR;
        }
        intReader.reset();

        switch (opCode) {
            // NOpE
            case 0, 1 -> {
                status = connectReader.process(bb);
                switch (status) {
                    case DONE:
                        packet = connectReader.get();
                        break;
                    case REFILL:
                        return ProcessStatus.REFILL;
                    case ERROR:
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                }
                connectReader.reset();
            }
            // NOPE
            case 4 -> {
                status = publicMessageReader.process(bb);
                switch (status) {
                    case DONE:
                        // send buffer to all connected clients
                        var packet = publicMessageReader.get();
                    case REFILL:
                        return ProcessStatus.REFILL;
                    case ERROR:
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                }
                publicMessageReader.reset();

                }
                // NOPE
                case 8 -> {
                    status = fusionInitReader.process(bb);
                    switch (status) {
                        case DONE :
                            // get packet from Reader
                            var packet = fusionInitReader.get();
                        case REFILL:
                            return ProcessStatus.REFILL;
                        case ERROR:
                            state = State.ERROR;
                            return ProcessStatus.ERROR;
                    }
                    fusionInitReader.reset();
                }
                // NOPE
                case 14 -> {
                    status = socketAddressReader.process(bb);
                    switch (status) {
                        case DONE :
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
        public Packet get () {
            return packet;
        }

        @Override
        public void reset () {
            state = State.WAITING;
        }
    }
