package entities;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class CallTest {

    @Test
    void defaultConstructor() {
        Call c = new Call();
        assertEquals(0, c.getId());
        assertNull(c.getStatus());
        assertEquals("audio", c.getCallType(), "getCallType() should default to audio when null");
    }

    @Test
    void threeArgConstructorDefaults() {
        Call c = new Call(1, 5, 10);
        assertEquals(1, c.getCallerId());
        assertEquals(5, c.getCalleeId());
        assertEquals(10, c.getRoomId());
        assertEquals("ringing", c.getStatus(), "Initial status should be ringing");
        assertEquals("audio", c.getCallType(), "Initial callType should be audio");
    }

    @Test
    void fullConstructor() {
        Timestamp started = Timestamp.valueOf("2025-06-01 14:00:00");
        Timestamp ended = Timestamp.valueOf("2025-06-01 14:30:00");
        Timestamp created = Timestamp.valueOf("2025-06-01 13:59:00");
        Call c = new Call(1, 2, 3, 10, "ended", "video", started, ended, created);

        assertEquals(1, c.getId());
        assertEquals(2, c.getCallerId());
        assertEquals(3, c.getCalleeId());
        assertEquals(10, c.getRoomId());
        assertEquals("ended", c.getStatus());
        assertEquals("video", c.getCallType());
        assertEquals(started, c.getStartedAt());
        assertEquals(ended, c.getEndedAt());
        assertEquals(created, c.getCreatedAt());
    }

    @Test
    void fullConstructorNullCallTypeFallsBackToAudio() {
        Call c = new Call(1, 2, 3, 10, "active", null,
                null, null, null);
        assertEquals("audio", c.getCallType());
    }

    @Test
    void isVideoCall() {
        Call c = new Call(1, 5, 10);
        assertFalse(c.isVideoCall());
        c.setCallType("video");
        assertTrue(c.isVideoCall());
    }

    @Test
    void isRinging() {
        Call c = new Call(1, 5, 10);
        assertTrue(c.isRinging());
        c.setStatus("active");
        assertFalse(c.isRinging());
    }

    @Test
    void isActive() {
        Call c = new Call(1, 5, 10);
        assertFalse(c.isActive());
        c.setStatus("active");
        assertTrue(c.isActive());
    }

    @Test
    void isEndedVariousStatuses() {
        Call c = new Call(1, 5, 10);
        assertFalse(c.isEnded(), "ringing should not be ended");

        c.setStatus("ended");
        assertTrue(c.isEnded());

        c.setStatus("rejected");
        assertTrue(c.isEnded());

        c.setStatus("missed");
        assertTrue(c.isEnded());

        c.setStatus("active");
        assertFalse(c.isEnded());
    }

    @Test
    void settersAndGetters() {
        Call c = new Call();
        c.setId(10);
        c.setCallerId(2);
        c.setCalleeId(8);
        c.setRoomId(15);
        c.setStatus("active");
        c.setCallType("video");
        Timestamp started = new Timestamp(System.currentTimeMillis());
        c.setStartedAt(started);
        Timestamp ended = new Timestamp(System.currentTimeMillis() + 60000);
        c.setEndedAt(ended);
        Timestamp created = new Timestamp(System.currentTimeMillis() - 60000);
        c.setCreatedAt(created);

        assertEquals(10, c.getId());
        assertEquals(2, c.getCallerId());
        assertEquals(8, c.getCalleeId());
        assertEquals(15, c.getRoomId());
        assertEquals("active", c.getStatus());
        assertEquals("video", c.getCallType());
        assertEquals(started, c.getStartedAt());
        assertEquals(ended, c.getEndedAt());
        assertEquals(created, c.getCreatedAt());
    }
}
