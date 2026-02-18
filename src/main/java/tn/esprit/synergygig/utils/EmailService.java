package tn.esprit.synergygig.utils;

public class EmailService {

    public static void send(String to, String subject, String body) {
        // Mock implementation - in a real app, use JavaMail API
        System.out.println("----- EMAIL SIMULATION -----");
        System.out.println("To: " + to);
        System.out.println("Subject: " + subject);
        System.out.println("Body: " + body);
        System.out.println("----------------------------");
    }
}
