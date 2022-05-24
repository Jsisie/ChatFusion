package fr.upem.net.tcp.chatfusion.Reader;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StringReaderTest {

    private final static int BUFFER_SIZE = 10;
    private final ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
    private final Charset cs = StandardCharsets.UTF_8;
    private final String car = "T";
    private final String msg = "TTTTTTTTTT";
    private final StringReader stringReader = new StringReader();

    public StringReaderTest() {
        fillBB();
    }

    private void fillBB() {
        IntStream.range(0, BUFFER_SIZE).forEach(__ -> bb.put(cs.encode(car)));
    }

    @Test
    void process() {
        var status = stringReader.process(bb.flip());
        assertEquals("REFILL", status.toString());
    }

    @Test
    void get() {
        stringReader.process(bb.flip());
        assertEquals(msg, stringReader.get());
    }

    @Test
    void reset() {
        stringReader.process(bb.flip());
        stringReader.reset();
        assertEquals(null,stringReader.get());
    }
}