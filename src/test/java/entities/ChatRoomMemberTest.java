package entities;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class ChatRoomMemberTest {

    @Test
    void defaultConstructor() {
        ChatRoomMember m = new ChatRoomMember();
        assertEquals(0, m.getId());
        assertEquals(0, m.getRoomId());
        assertEquals(0, m.getUserId());
        assertNull(m.getRole());
    }

    @Test
    void threeArgConstructor() {
        ChatRoomMember m = new ChatRoomMember(1, 5, "OWNER");
        assertEquals(1, m.getRoomId());
        assertEquals(5, m.getUserId());
        assertEquals("OWNER", m.getRole());
    }

    @Test
    void settersAndGetters() {
        ChatRoomMember m = new ChatRoomMember();
        m.setId(10);
        m.setRoomId(3);
        m.setUserId(7);
        m.setRole("ADMIN");
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        m.setJoinedAt(ts);

        assertEquals(10, m.getId());
        assertEquals(3, m.getRoomId());
        assertEquals(7, m.getUserId());
        assertEquals("ADMIN", m.getRole());
        assertEquals(ts, m.getJoinedAt());
    }

    @Test
    void toStringContainsFields() {
        ChatRoomMember m = new ChatRoomMember(2, 5, "MEMBER");
        String s = m.toString();
        assertTrue(s.contains("roomId=2"));
        assertTrue(s.contains("userId=5"));
        assertTrue(s.contains("MEMBER"));
    }
}
