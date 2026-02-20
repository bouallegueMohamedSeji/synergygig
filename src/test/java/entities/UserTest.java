package entities;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void testDefaultConstructorAndSetters() {
        User user = new User();
        user.setId(42);
        user.setEmail("alice@example.com");
        user.setPassword("secret");
        user.setFirstName("Alice");
        user.setLastName("Smith");
        user.setRole("EMPLOYEE");
        user.setAvatarPath("/img/alice.png");

        assertEquals(42, user.getId());
        assertEquals("alice@example.com", user.getEmail());
        assertEquals("secret", user.getPassword());
        assertEquals("Alice", user.getFirstName());
        assertEquals("Smith", user.getLastName());
        assertEquals("EMPLOYEE", user.getRole());
        assertEquals("/img/alice.png", user.getAvatarPath());
    }

    @Test
    void testRegistrationConstructor() {
        User user = new User("bob@example.com", "pass123", "Bob", "Jones", "HR_MANAGER");

        assertEquals("bob@example.com", user.getEmail());
        assertEquals("pass123", user.getPassword());
        assertEquals("Bob", user.getFirstName());
        assertEquals("Jones", user.getLastName());
        assertEquals("HR_MANAGER", user.getRole());
        assertEquals(0, user.getId(), "ID should default to 0 for new user");
        assertNull(user.getCreatedAt(), "CreatedAt should be null for registration constructor");
    }

    @Test
    void testFullConstructorWithAvatar() {
        Timestamp ts = Timestamp.valueOf(LocalDateTime.of(2026, 1, 15, 10, 30));
        User user = new User(5, "carol@example.com", "pwd", "Carol", "Lee", "ADMIN", ts, "/avatars/carol.jpg");

        assertEquals(5, user.getId());
        assertEquals("carol@example.com", user.getEmail());
        assertEquals("ADMIN", user.getRole());
        assertEquals(ts, user.getCreatedAt());
        assertEquals("/avatars/carol.jpg", user.getAvatarPath());
        assertNull(user.getFaceEncoding(), "Face encoding should be null when not provided");
    }

    @Test
    void testHasFaceEnrolled() {
        User user = new User();

        assertFalse(user.hasFaceEnrolled(), "Should be false when face encoding is null");

        user.setFaceEncoding("");
        assertFalse(user.hasFaceEnrolled(), "Should be false when face encoding is empty");

        user.setFaceEncoding("[0.12, 0.34, 0.56]");
        assertTrue(user.hasFaceEnrolled(), "Should be true when face encoding is set");
    }

    @Test
    void testToStringContainsKeyFields() {
        User user = new User("dave@example.com", "pass", "Dave", "Brown", "GIG_WORKER");
        user.setId(99);
        String str = user.toString();

        assertTrue(str.contains("99"), "toString should contain id");
        assertTrue(str.contains("dave@example.com"), "toString should contain email");
        assertTrue(str.contains("Dave"), "toString should contain firstName");
        assertTrue(str.contains("Brown"), "toString should contain lastName");
        assertTrue(str.contains("GIG_WORKER"), "toString should contain role");
    }
}
