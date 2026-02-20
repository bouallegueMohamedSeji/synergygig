package entities;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ChatRoomAndMessageTest {

    // --- ChatRoom Tests ---

    @Test
    void testChatRoomNameConstructor() {
        ChatRoom room = new ChatRoom("General");

        assertEquals("General", room.getName());
        assertEquals(0, room.getId());
        assertNull(room.getCreatedAt());
    }

    @Test
    void testChatRoomFullConstructor() {
        Timestamp ts = Timestamp.valueOf(LocalDateTime.of(2026, 2, 1, 8, 0));
        ChatRoom room = new ChatRoom(5, "Project Alpha", ts);

        assertEquals(5, room.getId());
        assertEquals("Project Alpha", room.getName());
        assertEquals(ts, room.getCreatedAt());
    }

    @Test
    void testChatRoomToStringShowsName() {
        ChatRoom room = new ChatRoom("Dev Chat");
        assertEquals("Dev Chat", room.toString());

        ChatRoom noName = new ChatRoom();
        noName.setId(7);
        assertEquals("Chat Room #7", noName.toString(), "Should fallback to 'Chat Room #<id>' when name is null");
    }

    // --- Message Tests ---

    @Test
    void testMessageCreationConstructor() {
        Message msg = new Message(1, 10, "Hello World");

        assertEquals(1, msg.getSenderId());
        assertEquals(10, msg.getRoomId());
        assertEquals("Hello World", msg.getContent());
        assertEquals(0, msg.getId(), "ID should be 0 before persistence");
        assertNull(msg.getTimestamp(), "Timestamp should be null before persistence");
    }

    @Test
    void testMessageFullConstructor() {
        Timestamp ts = Timestamp.valueOf(LocalDateTime.of(2026, 2, 18, 12, 30));
        Message msg = new Message(42, 3, 15, "Test message", ts);

        assertEquals(42, msg.getId());
        assertEquals(3, msg.getSenderId());
        assertEquals(15, msg.getRoomId());
        assertEquals("Test message", msg.getContent());
        assertEquals(ts, msg.getTimestamp());
    }

    @Test
    void testMessageSetters() {
        Message msg = new Message();
        msg.setId(100);
        msg.setSenderId(5);
        msg.setRoomId(20);
        msg.setContent("Updated content");
        Timestamp ts = Timestamp.valueOf(LocalDateTime.now());
        msg.setTimestamp(ts);

        assertEquals(100, msg.getId());
        assertEquals(5, msg.getSenderId());
        assertEquals(20, msg.getRoomId());
        assertEquals("Updated content", msg.getContent());
        assertEquals(ts, msg.getTimestamp());
    }

    @Test
    void testMessageToStringContainsFields() {
        Message msg = new Message(1, 2, "Hi there");
        msg.setId(55);
        String str = msg.toString();

        assertTrue(str.contains("55"), "toString should contain id");
        assertTrue(str.contains("Hi there"), "toString should contain content");
    }
}
