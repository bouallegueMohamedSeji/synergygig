package entities;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class ChatRoomTest {

    @Test
    void defaultConstructor() {
        ChatRoom r = new ChatRoom();
        assertEquals(0, r.getId());
        assertNull(r.getName());
        assertNull(r.getType());
    }

    @Test
    void singleArgConstructorSetsGroup() {
        ChatRoom r = new ChatRoom("General");
        assertEquals("General", r.getName());
        assertEquals("group", r.getType(), "Default type should be group");
    }

    @Test
    void threeArgConstructor() {
        ChatRoom r = new ChatRoom("DM Room", "private", 5);
        assertEquals("DM Room", r.getName());
        assertEquals("private", r.getType());
        assertEquals(5, r.getCreatedBy());
    }

    @Test
    void threeArgConstructorWithIdSetsGroup() {
        Timestamp ts = Timestamp.valueOf("2025-06-01 10:00:00");
        ChatRoom r = new ChatRoom(1, "Team Chat", ts);
        assertEquals(1, r.getId());
        assertEquals("Team Chat", r.getName());
        assertEquals(ts, r.getCreatedAt());
        assertEquals("group", r.getType());
    }

    @Test
    void fullConstructorNullTypeFallsBackToGroup() {
        Timestamp ts = Timestamp.valueOf("2025-06-01 10:00:00");
        ChatRoom r = new ChatRoom(1, "Room", ts, null, 3);
        assertEquals("group", r.getType(), "null type should fallback to group");
    }

    @Test
    void fullConstructor() {
        Timestamp ts = Timestamp.valueOf("2025-06-01 10:00:00");
        ChatRoom r = new ChatRoom(1, "Private Chat", ts, "private", 5);

        assertEquals(1, r.getId());
        assertEquals("Private Chat", r.getName());
        assertEquals(ts, r.getCreatedAt());
        assertEquals("private", r.getType());
        assertEquals(5, r.getCreatedBy());
    }

    @Test
    void isPrivateWithType() {
        ChatRoom r = new ChatRoom("DM", "private", 1);
        assertTrue(r.isPrivate());
    }

    @Test
    void isPrivateWithDmPrefix() {
        ChatRoom r = new ChatRoom("dm_1_2");
        r.setType("group"); // even if type is group, dm_ prefix means private
        assertTrue(r.isPrivate());
    }

    @Test
    void isPrivateFalseForGroup() {
        ChatRoom r = new ChatRoom("General");
        assertFalse(r.isPrivate());
    }

    @Test
    void settersAndGetters() {
        ChatRoom r = new ChatRoom();
        r.setId(10);
        r.setName("Test Room");
        r.setType("private");
        r.setCreatedBy(7);
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        r.setCreatedAt(ts);

        assertEquals(10, r.getId());
        assertEquals("Test Room", r.getName());
        assertEquals("private", r.getType());
        assertEquals(7, r.getCreatedBy());
        assertEquals(ts, r.getCreatedAt());
    }

    @Test
    void toStringWithName() {
        ChatRoom r = new ChatRoom("Dev Team");
        assertEquals("Dev Team", r.toString());
    }

    @Test
    void toStringWithNullName() {
        ChatRoom r = new ChatRoom();
        r.setId(5);
        assertEquals("Chat Room #5", r.toString());
    }
}
