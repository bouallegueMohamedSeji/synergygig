package entities;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class CommentTest {

    @Test
    void defaultConstructor() {
        Comment c = new Comment();
        assertEquals(0, c.getId());
        assertEquals(0, c.getPostId());
        assertNull(c.getContent());
        assertNull(c.getParentId());
    }

    @Test
    void threeArgConstructor() {
        Comment c = new Comment(1, 5, "Nice post!");
        assertEquals(1, c.getPostId());
        assertEquals(5, c.getAuthorId());
        assertEquals("Nice post!", c.getContent());
        assertNull(c.getParentId(), "Top-level comment should have null parentId");
    }

    @Test
    void fourArgConstructorForReply() {
        Comment c = new Comment(1, 5, "Reply text", 10);
        assertEquals(1, c.getPostId());
        assertEquals(5, c.getAuthorId());
        assertEquals("Reply text", c.getContent());
        assertEquals(10, c.getParentId(), "Reply should reference parent comment");
    }

    @Test
    void fullConstructor() {
        Timestamp ts = Timestamp.valueOf("2025-06-01 14:30:00");
        Comment c = new Comment(1, 2, 5, "Great!", ts);

        assertEquals(1, c.getId());
        assertEquals(2, c.getPostId());
        assertEquals(5, c.getAuthorId());
        assertEquals("Great!", c.getContent());
        assertEquals(ts, c.getCreatedAt());
    }

    @Test
    void settersAndGetters() {
        Comment c = new Comment();
        c.setId(7);
        c.setPostId(3);
        c.setAuthorId(12);
        c.setContent("Edited comment");
        c.setParentId(4);
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        c.setCreatedAt(ts);

        assertEquals(7, c.getId());
        assertEquals(3, c.getPostId());
        assertEquals(12, c.getAuthorId());
        assertEquals("Edited comment", c.getContent());
        assertEquals(4, c.getParentId());
        assertEquals(ts, c.getCreatedAt());
    }

    @Test
    void toStringContainsFields() {
        Comment c = new Comment(2, 5, "Text");
        c.setId(10);
        String s = c.toString();
        assertTrue(s.contains("id=10"));
        assertTrue(s.contains("postId=2"));
        assertTrue(s.contains("authorId=5"));
    }
}
