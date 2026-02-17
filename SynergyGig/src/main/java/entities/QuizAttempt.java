package entities;

import java.sql.Timestamp;

public class QuizAttempt {
    private int id;
    private int quizId;
    private int userId;
    private int score;
    private Timestamp attemptAt;

    public QuizAttempt() {
    }

    public QuizAttempt(int id, int quizId, int userId, int score, Timestamp attemptAt) {
        this.id = id;
        this.quizId = quizId;
        this.userId = userId;
        this.score = score;
        this.attemptAt = attemptAt;
    }

    public QuizAttempt(int quizId, int userId, int score) {
        this.quizId = quizId;
        this.userId = userId;
        this.score = score;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getQuizId() {
        return quizId;
    }

    public void setQuizId(int quizId) {
        this.quizId = quizId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public Timestamp getAttemptAt() {
        return attemptAt;
    }

    public void setAttemptAt(Timestamp attemptAt) {
        this.attemptAt = attemptAt;
    }

    @Override
    public String toString() {
        return "QuizAttempt{" +
                "id=" + id +
                ", quizId=" + quizId +
                ", userId=" + userId +
                ", score=" + score +
                '}';
    }
}
