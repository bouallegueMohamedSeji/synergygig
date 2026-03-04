package entities;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void defaultConstructor() {
        Message m = new Message();
        assertEquals(0, m.getId());
        assertEquals(0, m.getSenderId());
        assertEquals(0, m.getRoomId());
        assertNull(m.getContent());
        assertNull(m.getTimestamp());
    }

    @Test
    void threeArgConstructor() {
        Message m = new Message(5, 1, "Hello!");
        assertEquals(5, m.getSenderId());
        assertEquals(1, m.getRoomId());
        assertEquals("Hello!", m.getContent());
    }

    @Test
    void fullConstructor() {
        Timestamp ts = Timestamp.valueOf("2025-06-01 14:30:00");
        Message m = new Message(1, 5, 2, "Hi there", ts);

        assertEquals(1, m.getId());
        assertEquals(5, m.getSenderId());
        assertEquals(2, m.getRoomId());
        assertEquals("Hi there", m.getContent());
        assertEquals(ts, m.getTimestamp());
    }

    @Test
    void settersAndGetters() {
        Message m = new Message();
        m.setId(10);
        m.setSenderId(3);
        m.setRoomId(7);
        m.setContent("Updated message");
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        m.setTimestamp(ts);

        assertEquals(10, m.getId());
        assertEquals(3, m.getSenderId());
        assertEquals(7, m.getRoomId());
        assertEquals("Updated message", m.getContent());
        assertEquals(ts, m.getTimestamp());
    }

    @Test
    void toStringContainsFields() {
        Timestamp ts = Timestamp.valueOf("2025-06-01 12:00:00");
        Message m = new Message(1, 5, 2, "Test msg", ts);
        String s = m.toString();
        assertTrue(s.contains("id=1"));
        assertTrue(s.contains("senderId=5"));
        assertTrue(s.contains("roomId=2"));
        assertTrue(s.contains("Test msg"));
    }
}
