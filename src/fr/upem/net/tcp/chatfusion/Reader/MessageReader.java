package fr.upem.net.tcp.chatfusion.Reader;

import fr.upem.net.tcp.chatfusion.Message;

import java.nio.ByteBuffer;

public class MessageReader implements Reader<Message> {
    private enum State {DONE, WAITING, ERROR}

    private State state = State.WAITING;
    private Message value;
    String login = "";
    String message = "";
    private final StringReader stringReader = new StringReader();

    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }

        if (login.isEmpty()) {
            ProcessStatus status = stringReader.process(buffer);
            switch (status) {
                case DONE:
                    login = stringReader.get();
                    break;
                case REFILL:
                    return ProcessStatus.REFILL;
                case ERROR:
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
            }
            stringReader.reset();
        }

        if (message.isEmpty()) {
            ProcessStatus status = stringReader.process(buffer);
            switch (status) {
                case DONE:
                    message = stringReader.get();
                    break;
                case REFILL:
                    return ProcessStatus.REFILL;
                case ERROR:
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
            }
        }

        state = State.DONE;
        value = new Message(login, message);
        stringReader.reset();
        return ProcessStatus.DONE;
    }

    @Override
    public Message get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    public void reset() {
        state = State.WAITING;
        stringReader.reset();
        login = "";
        message = "";
    }
}
