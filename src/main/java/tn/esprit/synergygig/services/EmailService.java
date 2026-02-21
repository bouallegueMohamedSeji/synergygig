package tn.esprit.synergygig.services;

import jakarta.mail.*;
import jakarta.mail.internet.*;

import java.io.File;
import java.util.Properties;

public class EmailService {

    // 🔥 Elastic Email SMTP
    private static final String HOST = "smtp.elasticemail.com";
    private static final String USERNAME = "anas.chagour12@gmail.com"; // ton email Elastic
    private static final String PASSWORD = "";  // API KEY Elastic
    private static final int PORT = 2525; // 587 possible aussi

    public void sendContractEmail(
            String clientName,
            String pdfPath
    ) {

        try {

            Properties props = new Properties();
            props.put("mail.smtp.host", HOST);
            props.put("mail.smtp.port", String.valueOf(PORT));
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");

            Session session = Session.getInstance(props,
                    new Authenticator() {
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(USERNAME, PASSWORD);
                        }
                    });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(USERNAME));

            message.setRecipients(
                    Message.RecipientType.TO,
                    InternetAddress.parse("anas.chagour12@gmail.com")
            );

            message.setSubject("Votre contrat SynergyGig");

            // ===== HTML GALAXY =====
            MimeBodyPart htmlPart = new MimeBodyPart();

            String htmlContent = """
            <html>
            <body style="margin:0;padding:0;background:linear-gradient(135deg,#0f0f2d,#1a1a40);
            font-family:Arial;color:white;">

            <div style="max-width:600px;margin:40px auto;background:rgba(255,255,255,0.05);
            backdrop-filter:blur(15px);padding:40px;border-radius:20px;text-align:center;
            box-shadow:0 0 40px rgba(0,0,255,0.3);">

            <img src="https://i.imgur.com/YOUR_DIRECT_LOGO.png" width="140"/>

            <h2 style="color:#4da6ff;">Bonjour %s</h2>

            <p style="font-size:16px;">
            Votre contrat SynergyGig est prêt.
            </p>

            <div style="margin:25px 0;">
            <span style="display:inline-block;padding:10px 20px;
            background:#4da6ff;border-radius:30px;
            color:white;font-weight:bold;">
            Contrat joint en PDF
            </span>
            </div>

            <hr style="border:none;height:1px;background:#4da6ff;margin:30px 0;">

            <p style="font-size:14px;">
            <b>Anas Chagour</b><br>
            Founder & CEO – SynergyGig
            </p>

            <p style="font-size:12px;color:#aaa;">
            Secure • AI Verified • Blockchain Ready
            </p>

            </div>
            </body>
            </html>
            """.formatted(clientName);

            htmlPart.setContent(htmlContent, "text/html; charset=utf-8");

            // ===== PDF ATTACHMENT =====
            MimeBodyPart attachmentPart = new MimeBodyPart();
            attachmentPart.attachFile(new File(pdfPath));

            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(htmlPart);
            multipart.addBodyPart(attachmentPart);

            message.setContent(multipart);

            Transport.send(message);

            System.out.println("📧 Galaxy Email sent successfully");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}