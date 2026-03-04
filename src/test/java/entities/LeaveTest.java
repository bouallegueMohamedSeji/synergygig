package entities;

import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class LeaveTest {

    @Test
    void defaultConstructor() {
        Leave l = new Leave();
        assertEquals(0, l.getId());
        assertNull(l.getType());
        assertNull(l.getStatus());
    }

    @Test
    void parameterizedConstructorSetsPending() {
        Date start = Date.valueOf("2025-03-01");
        Date end = Date.valueOf("2025-03-05");
        Leave l = new Leave(10, "VACATION", start, end, "Holiday");

        assertEquals(10, l.getUserId());
        assertEquals("VACATION", l.getType());
        assertEquals(start, l.getStartDate());
        assertEquals(end, l.getEndDate());
        assertEquals("Holiday", l.getReason());
        assertEquals("PENDING", l.getStatus(), "Status should default to PENDING");
    }

    @Test
    void fullConstructor() {
        Date start = Date.valueOf("2025-04-01");
        Date end = Date.valueOf("2025-04-03");
        Timestamp ts = Timestamp.valueOf("2025-04-01 09:00:00");
        Leave l = new Leave(1, 10, "SICK", start, end, "Flu", "APPROVED", ts);

        assertEquals(1, l.getId());
        assertEquals(10, l.getUserId());
        assertEquals("SICK", l.getType());
        assertEquals("APPROVED", l.getStatus());
        assertEquals(ts, l.getCreatedAt());
    }

    @Test
    void leaveBalanceConstants() {
        assertEquals(30, Leave.MAX_VACATION_DAYS);
        assertEquals(15, Leave.MAX_SICK_DAYS);
        assertEquals(60, Leave.MAX_UNPAID_DAYS);
    }

    @Test
    void getDaysCalculation() {
        Date start = Date.valueOf("2025-06-01");
        Date end = Date.valueOf("2025-06-05");
        Leave l = new Leave(1, "VACATION", start, end, "Trip");
        assertEquals(5, l.getDays(), "June 1-5 should be 5 days inclusive");
    }

    @Test
    void getDaysWithNullDates() {
        Leave l = new Leave();
        assertEquals(0, l.getDays());
    }

    @Test
    void rejectionReason() {
        Leave l = new Leave();
        assertNull(l.getRejectionReason());
        l.setRejectionReason("Not enough days");
        assertEquals("Not enough days", l.getRejectionReason());
    }

    @Test
    void settersAndGetters() {
        Leave l = new Leave();
        l.setId(5);
        l.setUserId(20);
        l.setType("UNPAID");
        l.setStatus("REJECTED");
        Date d = Date.valueOf("2025-01-10");
        l.setStartDate(d);
        l.setEndDate(d);
        l.setReason("Personal");
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        l.setCreatedAt(ts);

        assertEquals(5, l.getId());
        assertEquals(20, l.getUserId());
        assertEquals("UNPAID", l.getType());
        assertEquals("REJECTED", l.getStatus());
        assertEquals(d, l.getStartDate());
        assertEquals(d, l.getEndDate());
        assertEquals("Personal", l.getReason());
        assertEquals(ts, l.getCreatedAt());
    }

    @Test
    void toStringContainsFields() {
        Leave l = new Leave(10, "SICK", Date.valueOf("2025-01-01"), Date.valueOf("2025-01-02"), "Cold");
        String s = l.toString();
        assertTrue(s.contains("userId=10"));
        assertTrue(s.contains("SICK"));
        assertTrue(s.contains("PENDING"));
    }
}
