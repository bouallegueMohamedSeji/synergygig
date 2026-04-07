package services;

import entities.User;
import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ServiceUserTest {

    static ServiceUser service;
    static int testUserId;

    @BeforeAll
    static void setup() {
        service = new ServiceUser();
    }

    @Test
    @Order(1)
    void testAjouterUser() throws SQLException {
        User user = new User("test@synergygig.com", "test123", "Test", "User", "EMPLOYEE");
        service.ajouter(user);

        List<User> users = service.recuperer();
        assertFalse(users.isEmpty(), "User list should not be empty after insert");

        boolean found = users.stream().anyMatch(u -> u.getEmail().equals("test@synergygig.com"));
        assertTrue(found, "Inserted user should exist in DB");

        // Store the ID for later tests
        testUserId = users.stream()
                .filter(u -> u.getEmail().equals("test@synergygig.com"))
                .findFirst()
                .map(User::getId)
                .orElse(-1);
        assertTrue(testUserId > 0, "Test user ID should be valid");
    }

    @Test
    @Order(2)
    void testRecuperer() throws SQLException {
        List<User> users = service.recuperer();
        assertNotNull(users, "User list should not be null");
        assertFalse(users.isEmpty(), "User list should not be empty");
    }

    @Test
    @Order(3)
    void testModifierUser() throws SQLException {
        User user = new User();
        user.setId(testUserId);
        user.setEmail("test@synergygig.com");
        user.setPassword("test123");
        user.setFirstName("Modified");
        user.setLastName("Name");
        user.setRole("EMPLOYEE");

        service.modifier(user);

        List<User> users = service.recuperer();
        boolean found = users.stream()
                .anyMatch(u -> u.getId() == testUserId && u.getFirstName().equals("Modified"));
        assertTrue(found, "User first name should be updated to 'Modified'");
    }

    @Test
    @Order(4)
    void testLogin() throws SQLException {
        User loggedIn = service.login("test@synergygig.com", "test123");
        assertNotNull(loggedIn, "Login should return a user with valid credentials");
        assertEquals("Modified", loggedIn.getFirstName());

        User failed = service.login("test@synergygig.com", "wrongpassword");
        assertNull(failed, "Login should return null with invalid credentials");
    }

    @Test
    @Order(5)
    void testEmailExists() throws SQLException {
        assertTrue(service.emailExists("test@synergygig.com"), "Email should exist");
        assertFalse(service.emailExists("nonexistent@synergygig.com"), "Email should not exist");
    }

    @Test
    @Order(6)
    void testUpdateRole() throws SQLException {
        service.updateRole(testUserId, "HR_MANAGER");

        List<User> users = service.recuperer();
        boolean found = users.stream()
                .anyMatch(u -> u.getId() == testUserId && u.getRole().equals("HR_MANAGER"));
        assertTrue(found, "User role should be updated to HR_MANAGER");
    }

    @Test
    @Order(7)
    void testSupprimerUser() throws SQLException {
        service.supprimer(testUserId);

        List<User> users = service.recuperer();
        boolean exists = users.stream().anyMatch(u -> u.getId() == testUserId);
        assertFalse(exists, "User should no longer exist after deletion");
    }
}
