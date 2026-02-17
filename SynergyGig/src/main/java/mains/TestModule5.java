package mains;

import entities.Course;
import entities.Quiz;
import entities.Resource;
import services.ServiceCourse;
import services.ServiceQuiz;
import services.ServiceResource;

import java.sql.SQLException;
import java.util.List;

public class TestModule5 {

    public static void main(String[] args) {
        ServiceCourse sc = new ServiceCourse();
        ServiceResource sr = new ServiceResource();
        ServiceQuiz sq = new ServiceQuiz();

        try {
            System.out.println("--- TESTING COURSE SERVICE ---");
            // Create
            Course c1 = new Course("Java Basics", "Learn Java from scratch", 1);
            sc.ajouter(c1);

            // Read
            List<Course> courses = sc.recuperer();
            System.out.println("Courses found: " + courses.size());
            int courseId = 0;
            if (!courses.isEmpty()) {
                Course lastCourse = courses.get(courses.size() - 1);
                System.out.println("Last Course: " + lastCourse);
                courseId = lastCourse.getId();

                // Update
                lastCourse.setTitle("Advanced Java");
                sc.modifier(lastCourse);
            }

            if (courseId != 0) {
                System.out.println("\n--- TESTING RESOURCE SERVICE ---");
                // Create
                Resource r1 = new Resource(courseId, "PDF", "http://example.com/java.pdf");
                sr.ajouter(r1);

                // Read
                List<Resource> resources = sr.recuperer();
                System.out.println("Resources found: " + resources.size());

                System.out.println("\n--- TESTING QUIZ SERVICE ---");
                // Create
                Quiz q1 = new Quiz(courseId, "Java Quiz 1");
                sq.ajouter(q1);

                // Read
                List<Quiz> quizzes = sq.recuperer();
                System.out.println("Quizzes found: " + quizzes.size());

                // Cleanup (Optional, but good for idempotent tests if we wanted)
                // sc.supprimer(courseId); // Will cascade delete resources and quizzes if DB is
                // set up correctly
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
