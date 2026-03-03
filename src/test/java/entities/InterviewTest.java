package entities;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class InterviewTest {

    @Test
    void testDefaultConstructorFields() {
        Interview interview = new Interview();

        assertEquals(0, interview.getId());
        assertEquals(0, interview.getOrganizerId());
        assertEquals(0, interview.getCandidateId());
        assertNull(interview.getDateTime());
        assertNull(interview.getStatus());
        assertNull(interview.getMeetLink());
    }

    @Test
    void testCreationConstructorSetsStatusPending() {
        Timestamp dt = Timestamp.valueOf(LocalDateTime.of(2026, 3, 20, 14, 0));
        Interview interview = new Interview(1, 2, dt, "https://zoom.us/j/123");

        assertEquals(1, interview.getOrganizerId());
        assertEquals(2, interview.getCandidateId());
        assertEquals(dt, interview.getDateTime());
        assertEquals("PENDING", interview.getStatus(), "New interviews should default to PENDING");
        assertEquals("https://zoom.us/j/123", interview.getMeetLink());
        assertEquals(0, interview.getId(), "ID should be 0 before persistence");
    }

    @Test
    void testSettersAndGetters() {
        Interview interview = new Interview();
        Timestamp dt = Timestamp.valueOf(LocalDateTime.now().plusDays(5));

        interview.setId(10);
        interview.setOrganizerId(3);
        interview.setCandidateId(7);
        interview.setDateTime(dt);
        interview.setStatus("ACCEPTED");
        interview.setMeetLink("https://meet.google.com/abc");

        assertEquals(10, interview.getId());
        assertEquals(3, interview.getOrganizerId());
        assertEquals(7, interview.getCandidateId());
        assertEquals(dt, interview.getDateTime());
        assertEquals("ACCEPTED", interview.getStatus());
        assertEquals("https://meet.google.com/abc", interview.getMeetLink());
    }
}
