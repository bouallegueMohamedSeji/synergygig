package entities;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class TrainingEnrollmentTest {

    @Test
    void defaultConstructor() {
        TrainingEnrollment te = new TrainingEnrollment();
        assertEquals(0, te.getId());
        assertEquals(0, te.getCourseId());
        assertNull(te.getStatus());
        assertEquals(0, te.getProgress());
        assertNull(te.getScore());
    }

    @Test
    void twoArgConstructorSetsDefaults() {
        TrainingEnrollment te = new TrainingEnrollment(10, 5);
        assertEquals(10, te.getCourseId());
        assertEquals(5, te.getUserId());
        assertEquals("ENROLLED", te.getStatus(), "Status should default to ENROLLED");
        assertEquals(0, te.getProgress(), "Progress should default to 0");
    }

    @Test
    void fullConstructor() {
        Timestamp enrolled = Timestamp.valueOf("2025-06-01 10:00:00");
        Timestamp completed = Timestamp.valueOf("2025-06-30 15:00:00");
        TrainingEnrollment te = new TrainingEnrollment(1, 10, 5, "COMPLETED", 100,
                92.5, enrolled, completed);

        assertEquals(1, te.getId());
        assertEquals(10, te.getCourseId());
        assertEquals(5, te.getUserId());
        assertEquals("COMPLETED", te.getStatus());
        assertEquals(100, te.getProgress());
        assertEquals(92.5, te.getScore());
        assertEquals(enrolled, te.getEnrolledAt());
        assertEquals(completed, te.getCompletedAt());
    }

    @Test
    void settersAndGetters() {
        TrainingEnrollment te = new TrainingEnrollment();
        te.setId(5);
        te.setCourseId(3);
        te.setUserId(7);
        te.setStatus("IN_PROGRESS");
        te.setProgress(55);
        te.setScore(78.5);
        Timestamp enrolled = new Timestamp(System.currentTimeMillis());
        te.setEnrolledAt(enrolled);
        Timestamp completed = new Timestamp(System.currentTimeMillis() + 100000);
        te.setCompletedAt(completed);

        assertEquals(5, te.getId());
        assertEquals(3, te.getCourseId());
        assertEquals(7, te.getUserId());
        assertEquals("IN_PROGRESS", te.getStatus());
        assertEquals(55, te.getProgress());
        assertEquals(78.5, te.getScore());
        assertEquals(enrolled, te.getEnrolledAt());
        assertEquals(completed, te.getCompletedAt());
    }

    @Test
    void nullableScore() {
        TrainingEnrollment te = new TrainingEnrollment(10, 5);
        assertNull(te.getScore(), "Score should be null initially");
        te.setScore(85.0);
        assertEquals(85.0, te.getScore());
        te.setScore(null);
        assertNull(te.getScore(), "Score should be nullable");
    }

    @Test
    void toStringContainsFields() {
        TrainingEnrollment te = new TrainingEnrollment(10, 5);
        String s = te.toString();
        assertTrue(s.contains("courseId=10"));
        assertTrue(s.contains("userId=5"));
        assertTrue(s.contains("ENROLLED"));
        assertTrue(s.contains("progress=0"));
    }
}
