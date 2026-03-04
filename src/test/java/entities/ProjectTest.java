package entities;

import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ProjectTest {

    @Test
    void testDefaultConstructor() {
        Project p = new Project();
        assertEquals(0, p.getId());
        assertNull(p.getName());
        assertNull(p.getStatus());
        assertEquals(0, p.getManagerId());
    }

    @Test
    void testParameterizedConstructor() {
        Date start = Date.valueOf(LocalDate.of(2026, 3, 1));
        Date deadline = Date.valueOf(LocalDate.of(2026, 9, 30));
        Project p = new Project("SynergyGig", "HR platform", 1, start, deadline, "PLANNING");

        assertEquals("SynergyGig", p.getName());
        assertEquals("HR platform", p.getDescription());
        assertEquals(1, p.getManagerId());
        assertEquals(start, p.getStartDate());
        assertEquals(deadline, p.getDeadline());
        assertEquals("PLANNING", p.getStatus());
    }

    @Test
    void testFullConstructor() {
        Date start = Date.valueOf(LocalDate.of(2026, 1, 1));
        Date deadline = Date.valueOf(LocalDate.of(2026, 6, 30));
        Timestamp ts = Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 9, 0));
        Project p = new Project(5, "Alpha", "Alpha project", 2, start, deadline, "IN_PROGRESS", ts);

        assertEquals(5, p.getId());
        assertEquals("Alpha", p.getName());
        assertEquals(ts, p.getCreatedAt());
    }

    @Test
    void testDepartmentId() {
        Project p = new Project();
        assertNull(p.getDepartmentId());
        p.setDepartmentId(10);
        assertEquals(10, p.getDepartmentId());
        p.setDepartmentId(null);
        assertNull(p.getDepartmentId());
    }

    @Test
    void testSettersAndGetters() {
        Project p = new Project();
        p.setId(99);
        p.setName("Beta");
        p.setDescription("Beta project");
        p.setManagerId(7);
        p.setStatus("COMPLETED");

        assertEquals(99, p.getId());
        assertEquals("Beta", p.getName());
        assertEquals("Beta project", p.getDescription());
        assertEquals(7, p.getManagerId());
        assertEquals("COMPLETED", p.getStatus());
    }

    @Test
    void testToString() {
        Project p = new Project();
        p.setId(1);
        p.setName("TestProject");
        String str = p.toString();
        assertNotNull(str);
        assertTrue(str.contains("TestProject"));
    }
}
