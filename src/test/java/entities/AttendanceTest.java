package entities;

import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class AttendanceTest {

    @Test
    void defaultConstructor() {
        Attendance a = new Attendance();
        assertEquals(0, a.getId());
        assertNull(a.getDate());
        assertNull(a.getCheckIn());
        assertNull(a.getCheckOut());
        assertNull(a.getStatus());
    }

    @Test
    void parameterizedConstructor() {
        Date d = Date.valueOf("2025-06-01");
        Time in = Time.valueOf("08:30:00");
        Time out = Time.valueOf("17:00:00");
        Attendance a = new Attendance(5, d, in, out, "PRESENT");

        assertEquals(5, a.getUserId());
        assertEquals(d, a.getDate());
        assertEquals(in, a.getCheckIn());
        assertEquals(out, a.getCheckOut());
        assertEquals("PRESENT", a.getStatus());
    }

    @Test
    void fullConstructor() {
        Date d = Date.valueOf("2025-06-01");
        Time in = Time.valueOf("09:00:00");
        Time out = Time.valueOf("17:30:00");
        Timestamp ts = Timestamp.valueOf("2025-06-01 09:00:00");
        Attendance a = new Attendance(1, 5, d, in, out, "LATE", ts);

        assertEquals(1, a.getId());
        assertEquals(5, a.getUserId());
        assertEquals("LATE", a.getStatus());
        assertEquals(ts, a.getCreatedAt());
    }

    @Test
    void getHoursWorked() {
        Time in = Time.valueOf("08:00:00");
        Time out = Time.valueOf("16:30:00");
        Attendance a = new Attendance(1, Date.valueOf("2025-01-01"), in, out, "PRESENT");
        assertEquals(8.5, a.getHoursWorked(), 0.01);
    }

    @Test
    void getHoursWorkedWithNullTimes() {
        Attendance a = new Attendance();
        assertEquals(0.0, a.getHoursWorked(), 0.001);
    }

    @Test
    void settersAndGetters() {
        Attendance a = new Attendance();
        a.setId(10);
        a.setUserId(3);
        Date d = Date.valueOf("2025-02-14");
        a.setDate(d);
        Time in = Time.valueOf("07:45:00");
        a.setCheckIn(in);
        Time out = Time.valueOf("16:00:00");
        a.setCheckOut(out);
        a.setStatus("EXCUSED");
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        a.setCreatedAt(ts);

        assertEquals(10, a.getId());
        assertEquals(3, a.getUserId());
        assertEquals(d, a.getDate());
        assertEquals(in, a.getCheckIn());
        assertEquals(out, a.getCheckOut());
        assertEquals("EXCUSED", a.getStatus());
        assertEquals(ts, a.getCreatedAt());
    }

    @Test
    void toStringContainsFields() {
        Attendance a = new Attendance(7, Date.valueOf("2025-03-15"), Time.valueOf("08:00:00"),
                Time.valueOf("17:00:00"), "PRESENT");
        String s = a.toString();
        assertTrue(s.contains("userId=7"));
        assertTrue(s.contains("PRESENT"));
    }
}
