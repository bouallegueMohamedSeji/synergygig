package entities;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class UserFollowTest {

    @Test
    void defaultConstructor() {
        UserFollow uf = new UserFollow();
        assertEquals(0, uf.getId());
        assertEquals(0, uf.getFollowerId());
        assertEquals(0, uf.getFollowedId());
        assertEquals(UserFollow.STATUS_ACCEPTED, uf.getStatus(),
                "Default status should be ACCEPTED");
    }

    @Test
    void statusConstants() {
        assertEquals("ACCEPTED", UserFollow.STATUS_ACCEPTED);
        assertEquals("PENDING", UserFollow.STATUS_PENDING);
    }

    @Test
    void twoArgConstructor() {
        UserFollow uf = new UserFollow(1, 5);
        assertEquals(1, uf.getFollowerId());
        assertEquals(5, uf.getFollowedId());
    }

    @Test
    void threeArgConstructorWithStatus() {
        UserFollow uf = new UserFollow(1, 5, "PENDING");
        assertEquals(1, uf.getFollowerId());
        assertEquals(5, uf.getFollowedId());
        assertEquals("PENDING", uf.getStatus());
    }

    @Test
    void fourArgConstructor() {
        Timestamp ts = Timestamp.valueOf("2025-06-01 12:00:00");
        UserFollow uf = new UserFollow(1, 3, 7, ts);

        assertEquals(1, uf.getId());
        assertEquals(3, uf.getFollowerId());
        assertEquals(7, uf.getFollowedId());
        assertEquals(ts, uf.getCreatedAt());
    }

    @Test
    void settersAndGetters() {
        UserFollow uf = new UserFollow();
        uf.setId(10);
        uf.setFollowerId(2);
        uf.setFollowedId(8);
        uf.setStatus("PENDING");
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        uf.setCreatedAt(ts);

        assertEquals(10, uf.getId());
        assertEquals(2, uf.getFollowerId());
        assertEquals(8, uf.getFollowedId());
        assertEquals("PENDING", uf.getStatus());
        assertEquals(ts, uf.getCreatedAt());
    }

    @Test
    void toStringContainsFields() {
        UserFollow uf = new UserFollow(3, 7);
        String s = uf.toString();
        assertTrue(s.contains("follower=3"));
        assertTrue(s.contains("followed=7"));
    }
}
