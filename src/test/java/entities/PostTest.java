package entities;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class PostTest {

    @Test
    void defaultConstructor() {
        Post p = new Post();
        assertEquals(0, p.getId());
        assertEquals(0, p.getAuthorId());
        assertNull(p.getContent());
        assertNull(p.getImageBase64());
        assertEquals(Post.VISIBILITY_PUBLIC, p.getVisibility(), "Default visibility should be PUBLIC");
    }

    @Test
    void visibilityConstants() {
        assertEquals("PUBLIC", Post.VISIBILITY_PUBLIC);
        assertEquals("FRIENDS", Post.VISIBILITY_FRIENDS);
        assertEquals("ONLY_ME", Post.VISIBILITY_ONLY_ME);
    }

    @Test
    void twoArgConstructor() {
        Post p = new Post(5, "Hello world");
        assertEquals(5, p.getAuthorId());
        assertEquals("Hello world", p.getContent());
    }

    @Test
    void threeArgConstructor() {
        Post p = new Post(5, "Photo post", "base64data");
        assertEquals(5, p.getAuthorId());
        assertEquals("Photo post", p.getContent());
        assertEquals("base64data", p.getImageBase64());
    }

    @Test
    void fullConstructor() {
        Timestamp ts = Timestamp.valueOf("2025-06-01 12:00:00");
        Post p = new Post(1, 5, "Content", "imgData", 10, 3, ts);

        assertEquals(1, p.getId());
        assertEquals(5, p.getAuthorId());
        assertEquals("Content", p.getContent());
        assertEquals("imgData", p.getImageBase64());
        assertEquals(10, p.getLikesCount());
        assertEquals(3, p.getCommentsCount());
        assertEquals(ts, p.getCreatedAt());
    }

    @Test
    void groupIdNullByDefault() {
        Post p = new Post(1, "text");
        assertNull(p.getGroupId(), "Normal post should have null groupId");
    }

    @Test
    void settersAndGetters() {
        Post p = new Post();
        p.setId(10);
        p.setAuthorId(3);
        p.setContent("Updated content");
        p.setImageBase64("newImg");
        p.setLikesCount(50);
        p.setCommentsCount(12);
        p.setVisibility(Post.VISIBILITY_FRIENDS);
        p.setGroupId(7);
        p.setSharesCount(5);
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        p.setCreatedAt(ts);

        assertEquals(10, p.getId());
        assertEquals(3, p.getAuthorId());
        assertEquals("Updated content", p.getContent());
        assertEquals("newImg", p.getImageBase64());
        assertEquals(50, p.getLikesCount());
        assertEquals(12, p.getCommentsCount());
        assertEquals("FRIENDS", p.getVisibility());
        assertEquals(7, p.getGroupId());
        assertEquals(5, p.getSharesCount());
        assertEquals(ts, p.getCreatedAt());
    }

    @Test
    void toStringTruncatesLongContent() {
        String longContent = "A".repeat(100);
        Post p = new Post(1, longContent);
        p.setId(1);
        String s = p.toString();
        assertTrue(s.contains("..."), "Long content should be truncated with ...");
    }

    @Test
    void toStringShortContent() {
        Post p = new Post(1, "Short");
        p.setId(1);
        String s = p.toString();
        assertTrue(s.contains("Short"));
        assertFalse(s.contains("..."));
    }
}
