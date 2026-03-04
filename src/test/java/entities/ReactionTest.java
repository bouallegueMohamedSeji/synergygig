package entities;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class ReactionTest {

    @Test
    void defaultConstructor() {
        Reaction r = new Reaction();
        assertEquals(0, r.getId());
        assertEquals(0, r.getPostId());
        assertNull(r.getType());
    }

    @Test
    void threeArgConstructor() {
        Reaction r = new Reaction(1, 5, "heart");
        assertEquals(1, r.getPostId());
        assertEquals(5, r.getUserId());
        assertEquals("heart", r.getType());
    }

    @Test
    void fullConstructor() {
        Timestamp ts = Timestamp.valueOf("2025-06-01 15:00:00");
        Reaction r = new Reaction(1, 2, 5, "fire", ts);

        assertEquals(1, r.getId());
        assertEquals(2, r.getPostId());
        assertEquals(5, r.getUserId());
        assertEquals("fire", r.getType());
        assertEquals(ts, r.getCreatedAt());
    }

    @Test
    void settersAndGetters() {
        Reaction r = new Reaction();
        r.setId(10);
        r.setPostId(3);
        r.setUserId(7);
        r.setType("laugh");
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        r.setCreatedAt(ts);

        assertEquals(10, r.getId());
        assertEquals(3, r.getPostId());
        assertEquals(7, r.getUserId());
        assertEquals("laugh", r.getType());
        assertEquals(ts, r.getCreatedAt());
    }

    @Test
    void toStringContainsFields() {
        Reaction r = new Reaction(2, 5, "like");
        r.setId(1);
        String s = r.toString();
        assertTrue(s.contains("id=1"));
        assertTrue(s.contains("postId=2"));
        assertTrue(s.contains("userId=5"));
        assertTrue(s.contains("like"));
    }
}
