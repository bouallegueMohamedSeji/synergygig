package entities;

import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class TrainingCourseTest {

    @Test
    void defaultConstructor() {
        TrainingCourse tc = new TrainingCourse();
        assertEquals(0, tc.getId());
        assertNull(tc.getTitle());
        assertNull(tc.getStatus());
        assertEquals(0, tc.getQuizTimerSeconds());
    }

    @Test
    void parameterizedConstructor() {
        Date start = Date.valueOf("2025-07-01");
        Date end = Date.valueOf("2025-07-31");
        TrainingCourse tc = new TrainingCourse("Java Basics", "Learn Java", "TECHNICAL",
                "BEGINNER", 40.0, "Dr. Smith", "https://mega.nz/folder/abc",
                30, "ACTIVE", start, end, 5);

        assertEquals("Java Basics", tc.getTitle());
        assertEquals("Learn Java", tc.getDescription());
        assertEquals("TECHNICAL", tc.getCategory());
        assertEquals("BEGINNER", tc.getDifficulty());
        assertEquals(40.0, tc.getDurationHours());
        assertEquals("Dr. Smith", tc.getInstructorName());
        assertEquals("https://mega.nz/folder/abc", tc.getMegaLink());
        assertEquals(30, tc.getMaxParticipants());
        assertEquals("ACTIVE", tc.getStatus());
        assertEquals(start, tc.getStartDate());
        assertEquals(end, tc.getEndDate());
        assertEquals(5, tc.getCreatedBy());
    }

    @Test
    void fullConstructor() {
        Date start = Date.valueOf("2025-07-01");
        Date end = Date.valueOf("2025-07-31");
        Timestamp ts = Timestamp.valueOf("2025-06-15 10:00:00");
        TrainingCourse tc = new TrainingCourse(1, "Advanced ML", "Machine Learning", "TECHNICAL",
                "ADVANCED", 80.0, "Prof. Lee", "https://mega.nz/folder/xyz",
                "https://img.com/thumb.png", 20, "DRAFT", start, end, 3, ts);

        assertEquals(1, tc.getId());
        assertEquals("Advanced ML", tc.getTitle());
        assertEquals("https://img.com/thumb.png", tc.getThumbnailUrl());
        assertEquals(ts, tc.getCreatedAt());
    }

    @Test
    void effectiveQuizTimerCustomValue() {
        TrainingCourse tc = new TrainingCourse();
        tc.setQuizTimerSeconds(20);
        assertEquals(20, tc.getEffectiveQuizTimer(),
                "Custom timer should be used when > 0");
    }

    @Test
    void effectiveQuizTimerBeginner() {
        TrainingCourse tc = new TrainingCourse();
        tc.setDifficulty("BEGINNER");
        assertEquals(10, tc.getEffectiveQuizTimer());
    }

    @Test
    void effectiveQuizTimerIntermediate() {
        TrainingCourse tc = new TrainingCourse();
        tc.setDifficulty("INTERMEDIATE");
        assertEquals(12, tc.getEffectiveQuizTimer());
    }

    @Test
    void effectiveQuizTimerAdvanced() {
        TrainingCourse tc = new TrainingCourse();
        tc.setDifficulty("ADVANCED");
        assertEquals(15, tc.getEffectiveQuizTimer());
    }

    @Test
    void effectiveQuizTimerNullDifficulty() {
        TrainingCourse tc = new TrainingCourse();
        // difficulty is null, quizTimerSeconds is 0 → default 10
        assertEquals(10, tc.getEffectiveQuizTimer());
    }

    @Test
    void settersAndGetters() {
        TrainingCourse tc = new TrainingCourse();
        tc.setId(5);
        tc.setTitle("Python 101");
        tc.setDescription("Intro");
        tc.setCategory("SOFT_SKILLS");
        tc.setDifficulty("INTERMEDIATE");
        tc.setDurationHours(20.0);
        tc.setInstructorName("Jane");
        tc.setMegaLink("https://mega.nz/test");
        tc.setThumbnailUrl("https://img.com/t.png");
        tc.setMaxParticipants(50);
        tc.setStatus("ARCHIVED");
        Date start = Date.valueOf("2025-01-01");
        Date end = Date.valueOf("2025-01-31");
        tc.setStartDate(start);
        tc.setEndDate(end);
        tc.setCreatedBy(10);
        tc.setQuizTimerSeconds(15);
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        tc.setCreatedAt(ts);

        assertEquals(5, tc.getId());
        assertEquals("Python 101", tc.getTitle());
        assertEquals("Intro", tc.getDescription());
        assertEquals("SOFT_SKILLS", tc.getCategory());
        assertEquals("INTERMEDIATE", tc.getDifficulty());
        assertEquals(20.0, tc.getDurationHours());
        assertEquals("Jane", tc.getInstructorName());
        assertEquals("https://mega.nz/test", tc.getMegaLink());
        assertEquals("https://img.com/t.png", tc.getThumbnailUrl());
        assertEquals(50, tc.getMaxParticipants());
        assertEquals("ARCHIVED", tc.getStatus());
        assertEquals(start, tc.getStartDate());
        assertEquals(end, tc.getEndDate());
        assertEquals(10, tc.getCreatedBy());
        assertEquals(15, tc.getQuizTimerSeconds());
        assertEquals(ts, tc.getCreatedAt());
    }

    @Test
    void toStringContainsFields() {
        TrainingCourse tc = new TrainingCourse();
        tc.setId(1);
        tc.setTitle("Testing");
        tc.setCategory("COMPLIANCE");
        tc.setStatus("ACTIVE");
        String s = tc.toString();
        assertTrue(s.contains("id=1"));
        assertTrue(s.contains("Testing"));
        assertTrue(s.contains("COMPLIANCE"));
        assertTrue(s.contains("ACTIVE"));
    }
}
