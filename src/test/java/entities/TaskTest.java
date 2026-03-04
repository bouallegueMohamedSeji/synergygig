package entities;

import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TaskTest {

    @Test
    void testDefaultConstructor() {
        Task t = new Task();
        assertEquals(0, t.getId());
        assertNull(t.getTitle());
        assertNull(t.getStatus());
        assertNull(t.getPriority());
    }

    @Test
    void testParameterizedConstructor() {
        Date due = Date.valueOf(LocalDate.of(2026, 5, 15));
        Task t = new Task(1, 2, "Implement login", "Login feature", "TODO", "HIGH", due);

        assertEquals(1, t.getProjectId());
        assertEquals(2, t.getAssigneeId());
        assertEquals("Implement login", t.getTitle());
        assertEquals("Login feature", t.getDescription());
        assertEquals("TODO", t.getStatus());
        assertEquals("HIGH", t.getPriority());
        assertEquals(due, t.getDueDate());
    }

    @Test
    void testFullConstructor() {
        Date due = Date.valueOf(LocalDate.of(2026, 4, 1));
        Timestamp ts = Timestamp.valueOf(LocalDateTime.of(2026, 3, 1, 10, 0));
        Task t = new Task(10, 1, 3, "Fix bug", "Critical bug", "IN_PROGRESS", "MEDIUM", due, ts);

        assertEquals(10, t.getId());
        assertEquals(1, t.getProjectId());
        assertEquals(3, t.getAssigneeId());
        assertEquals("Fix bug", t.getTitle());
        assertEquals("IN_PROGRESS", t.getStatus());
        assertEquals("MEDIUM", t.getPriority());
        assertEquals(ts, t.getCreatedAt());
    }

    @Test
    void testSettersAndGetters() {
        Task t = new Task();
        t.setId(42);
        t.setProjectId(5);
        t.setAssigneeId(8);
        t.setTitle("Write tests");
        t.setDescription("Unit tests for all modules");
        t.setStatus("DONE");
        t.setPriority("LOW");

        assertEquals(42, t.getId());
        assertEquals(5, t.getProjectId());
        assertEquals(8, t.getAssigneeId());
        assertEquals("Write tests", t.getTitle());
        assertEquals("Unit tests for all modules", t.getDescription());
        assertEquals("DONE", t.getStatus());
        assertEquals("LOW", t.getPriority());
    }

    @Test
    void testToString() {
        Task t = new Task();
        t.setId(1);
        t.setTitle("Test task");
        String str = t.toString();
        assertNotNull(str);
        assertTrue(str.contains("Test task"));
    }
}
