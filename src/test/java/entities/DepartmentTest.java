package entities;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DepartmentTest {

    @Test
    void testDefaultConstructor() {
        Department d = new Department();
        assertEquals(0, d.getId());
        assertNull(d.getName());
        assertNull(d.getDescription());
        assertNull(d.getManagerId());
        assertEquals(0.0, d.getAllocatedBudget());
    }

    @Test
    void testParameterizedConstructor() {
        Department d = new Department("Engineering", "Software engineering dept", 5, 100000.0);

        assertEquals("Engineering", d.getName());
        assertEquals("Software engineering dept", d.getDescription());
        assertEquals(5, d.getManagerId());
        assertEquals(100000.0, d.getAllocatedBudget());
    }

    @Test
    void testFullConstructor() {
        Timestamp ts = Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 9, 0));
        Department d = new Department(3, "HR", "Human Resources", 2, 50000.0, ts);

        assertEquals(3, d.getId());
        assertEquals("HR", d.getName());
        assertEquals("Human Resources", d.getDescription());
        assertEquals(2, d.getManagerId());
        assertEquals(50000.0, d.getAllocatedBudget());
        assertEquals(ts, d.getCreatedAt());
    }

    @Test
    void testNullManagerId() {
        Department d = new Department("Sales", "Sales team", null, 75000.0);
        assertNull(d.getManagerId());
    }

    @Test
    void testSettersAndGetters() {
        Department d = new Department();
        d.setId(10);
        d.setName("Marketing");
        d.setDescription("Marketing department");
        d.setManagerId(8);
        d.setAllocatedBudget(60000.0);

        assertEquals(10, d.getId());
        assertEquals("Marketing", d.getName());
        assertEquals("Marketing department", d.getDescription());
        assertEquals(8, d.getManagerId());
        assertEquals(60000.0, d.getAllocatedBudget());
    }

    @Test
    void testToString() {
        Department d = new Department("IT", "IT Department", 1, 80000.0);
        d.setId(1);
        String str = d.toString();
        assertNotNull(str);
        assertTrue(str.contains("IT"));
    }
}
