package services;

import entities.ChatRoom;
import entities.Interview;
import org.junit.jupiter.api.*;
import utils.MyDatabase;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ServiceCommunicationTest {

    static ServiceChatRoom serviceChat;
    static ServiceInterview serviceInterview;
    static int roomId;
    static int interviewId;

    @BeforeAll
    static void setup() {
        serviceChat = new ServiceChatRoom();
        serviceInterview = new ServiceInterview();
    }

    // --- CHAT ROOM TESTS ---

    @Test
    @Order(1)
    void testCreateChatRoom() throws SQLException {
        ChatRoom room = new ChatRoom("Test Room");
        serviceChat.ajouter(room);

        List<ChatRoom> rooms = serviceChat.recuperer();
        boolean found = rooms.stream().anyMatch(r -> r.getName().equals("Test Room"));
        assertTrue(found, "Chat room should be created");

        roomId = rooms.stream()
                .filter(r -> r.getName().equals("Test Room"))
                .findFirst()
                .map(ChatRoom::getId)
                .orElse(-1);
    }

    @Test
    @Order(2)
    void testGetOrCreateRoom() throws SQLException {
        ChatRoom room = serviceChat.getOrCreateRoom("Test Room");
        assertEquals(roomId, room.getId(), "Should return existing room ID");

        ChatRoom newRoom = serviceChat.getOrCreateRoom("New Room");
        assertTrue(newRoom.getId() > 0, "Should create and return new room ID");

        // Clean up "New Room" immediately
        serviceChat.supprimer(newRoom.getId());
    }

    // --- INTERVIEW TESTS ---

    @Test
    @Order(3)
    void testScheduleInterview() throws SQLException {
        // Assuming user ID 1 exists as organizer/candidate for test purposes
        // Ideally we should create users in @BeforeAll, but we rely on pre-existing
        // data or mock it
        // Here we just test the insertion logic with dummy IDs (MySQL might enforce FK
        // constraint though)
        // If strict FK, we need valid IDs. Let's assume ID 1 exists (from previous
        // walkthrough seed).

        Interview interview = new Interview(1, 1, Timestamp.valueOf(LocalDateTime.now().plusDays(1)), "zoom.us/j/123");
        serviceInterview.ajouter(interview);

        List<Interview> list = serviceInterview.recuperer();
        assertFalse(list.isEmpty());

        interviewId = list.get(0).getId();
    }

    @Test
    @Order(4)
    void testUpdateInterview() throws SQLException {
        if (interviewId == 0)
            return; // Skip if insert failed

        Interview interview = new Interview();
        interview.setId(interviewId);
        interview.setDateTime(Timestamp.valueOf(LocalDateTime.now().plusDays(2)));
        interview.setStatus("ACCEPTED");
        interview.setMeetLink("meet.google.com/abc");

        serviceInterview.modifier(interview);

        List<Interview> list = serviceInterview.recuperer();
        Interview updated = list.stream().filter(i -> i.getId() == interviewId).findFirst().orElse(null);
        assertNotNull(updated);
        assertEquals("ACCEPTED", updated.getStatus());
    }

    @AfterEach
    void cleanUp() throws SQLException {
        // Cleanup happens naturally or we can explicit delete here
    }

    @AfterAll
    static void finalClean() throws SQLException {
        if (roomId > 0)
            serviceChat.supprimer(roomId);
        if (interviewId > 0)
            serviceInterview.supprimer(interviewId);
    }
}
