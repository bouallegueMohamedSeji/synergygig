package entities;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class CommunityGroupTest {

    @Test
    void defaultConstructorSetsPublicPrivacy() {
        CommunityGroup g = new CommunityGroup();
        assertEquals(CommunityGroup.PRIVACY_PUBLIC, g.getPrivacy(),
                "Default constructor should set privacy to PUBLIC");
        assertEquals(0, g.getId());
    }

    @Test
    void privacyConstants() {
        assertEquals("PUBLIC", CommunityGroup.PRIVACY_PUBLIC);
        assertEquals("PRIVATE", CommunityGroup.PRIVACY_PRIVATE);
    }

    @Test
    void threeArgConstructor() {
        CommunityGroup g = new CommunityGroup("Java Club", "For Java lovers", 5);
        assertEquals("Java Club", g.getName());
        assertEquals("For Java lovers", g.getDescription());
        assertEquals(5, g.getCreatorId());
        assertEquals("PUBLIC", g.getPrivacy());
    }

    @Test
    void fullConstructor() {
        Timestamp ts = Timestamp.valueOf("2025-06-01 10:00:00");
        CommunityGroup g = new CommunityGroup(1, "Secret", "Private group",
                "imgData", 3, "PRIVATE", 25, ts);

        assertEquals(1, g.getId());
        assertEquals("Secret", g.getName());
        assertEquals("Private group", g.getDescription());
        assertEquals("imgData", g.getImageBase64());
        assertEquals(3, g.getCreatorId());
        assertEquals("PRIVATE", g.getPrivacy());
        assertEquals(25, g.getMemberCount());
        assertEquals(ts, g.getCreatedAt());
    }

    @Test
    void settersAndGetters() {
        CommunityGroup g = new CommunityGroup();
        g.setId(10);
        g.setName("Test Group");
        g.setDescription("A test");
        g.setImageBase64("base64");
        g.setCreatorId(7);
        g.setPrivacy("PRIVATE");
        g.setMemberCount(50);
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        g.setCreatedAt(ts);

        assertEquals(10, g.getId());
        assertEquals("Test Group", g.getName());
        assertEquals("A test", g.getDescription());
        assertEquals("base64", g.getImageBase64());
        assertEquals(7, g.getCreatorId());
        assertEquals("PRIVATE", g.getPrivacy());
        assertEquals(50, g.getMemberCount());
        assertEquals(ts, g.getCreatedAt());
    }

    @Test
    void toStringContainsFields() {
        CommunityGroup g = new CommunityGroup("DevOps", "DevOps group", 1);
        g.setId(3);
        g.setMemberCount(15);
        String s = g.toString();
        assertTrue(s.contains("id=3"));
        assertTrue(s.contains("DevOps"));
        assertTrue(s.contains("members=15"));
    }
}
