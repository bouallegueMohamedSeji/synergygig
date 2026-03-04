package entities;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class GroupMemberTest {

    @Test
    void defaultConstructorSetsMemberRole() {
        GroupMember gm = new GroupMember();
        assertEquals(GroupMember.ROLE_MEMBER, gm.getRole(),
                "Default role should be MEMBER");
        assertEquals(0, gm.getId());
    }

    @Test
    void roleConstants() {
        assertEquals("ADMIN", GroupMember.ROLE_ADMIN);
        assertEquals("MEMBER", GroupMember.ROLE_MEMBER);
    }

    @Test
    void threeArgConstructor() {
        GroupMember gm = new GroupMember(1, 5, "ADMIN");
        assertEquals(1, gm.getGroupId());
        assertEquals(5, gm.getUserId());
        assertEquals("ADMIN", gm.getRole());
    }

    @Test
    void fullConstructor() {
        Timestamp ts = Timestamp.valueOf("2025-06-01 10:00:00");
        GroupMember gm = new GroupMember(1, 2, 5, "MEMBER", ts);

        assertEquals(1, gm.getId());
        assertEquals(2, gm.getGroupId());
        assertEquals(5, gm.getUserId());
        assertEquals("MEMBER", gm.getRole());
        assertEquals(ts, gm.getJoinedAt());
    }

    @Test
    void settersAndGetters() {
        GroupMember gm = new GroupMember();
        gm.setId(10);
        gm.setGroupId(3);
        gm.setUserId(7);
        gm.setRole("ADMIN");
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        gm.setJoinedAt(ts);

        assertEquals(10, gm.getId());
        assertEquals(3, gm.getGroupId());
        assertEquals(7, gm.getUserId());
        assertEquals("ADMIN", gm.getRole());
        assertEquals(ts, gm.getJoinedAt());
    }

    @Test
    void toStringContainsFields() {
        GroupMember gm = new GroupMember(2, 5, "MEMBER");
        String s = gm.toString();
        assertTrue(s.contains("groupId=2"));
        assertTrue(s.contains("userId=5"));
        assertTrue(s.contains("MEMBER"));
    }
}
