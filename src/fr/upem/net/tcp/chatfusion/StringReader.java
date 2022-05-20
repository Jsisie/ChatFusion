package fr.upem.net.tcp.chatfusion;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;

public class StringReader implements Reader<String> {

    private enum State {
        DONE, WAITING, ERROR, SIZE
    };

    private State state = State.SIZE;
    private final int BUFFER_SIZE = 1024;
    private final IntReader intReader = new IntReader();
    private ByteBuffer internalBuffer = ByteBuffer.allocate(BUFFER_SIZE);;
    private final Charset UTF8 = StandardCharsets.UTF_8;
    private final Predicate<Integer> isValidSize = (Integer number) -> number > 0 && number <= BUFFER_SIZE;
    private String value;
    private int size;


    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        if(state.equals(State.SIZE)) {
            extractSize(buffer);
        }
        buffer.flip();
        try {
            if(state.equals(State.WAITING)) {
                extractString(buffer);
            }
        } finally {
            buffer.compact();
        }

        if(state.equals(State.ERROR)) {
            return ProcessStatus.ERROR;
        }

        if (internalBuffer.hasRemaining()) {
            return ProcessStatus.REFILL;
        }
        state = State.DONE;
        internalBuffer.flip();
        value = UTF8.decode(internalBuffer).toString();
        return ProcessStatus.DONE;
    }

    private void extractSize(ByteBuffer buffer) {
       var intState =  intReader.process(buffer);

        if(intState.equals(ProcessStatus.DONE)) {
           size = intReader.get();
           if(!isValidSize.test(size)) {
               state = State.ERROR;
               return;
           }
           state = State.WAITING;
           internalBuffer.limit(size);
       } else if(intState.equals(ProcessStatus.ERROR)) {
            state = State.ERROR;
        }

    }


    private void extractString(ByteBuffer buffer) {
        if(!state.equals(State.WAITING)) {
            return;
        }
        if (buffer.remaining() <= internalBuffer.remaining()) {
            internalBuffer.put(buffer);
        } else {
            var oldLimit = buffer.limit();
            buffer.limit(internalBuffer.remaining());
            internalBuffer.put(buffer);
            buffer.limit(oldLimit);
        }
    }

    @Override
    public String get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }

        return value;
    }

    @Override
    public void reset() {
        state = State.SIZE;
        internalBuffer.clear();
        intReader.reset();
        value = null;
    }
}