package utils;

import javax.mail.*;
import javax.mail.internet.*;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

/**
 * Sends styled HTML emails via Gmail SMTP.
 * Credentials are loaded from config.properties (smtp.email / smtp.password).
 */
public class EmailService {

    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final int SMTP_PORT = 587;

    /* ───────── shared session builder ───────── */

    private static Session buildSession() {
        String fromEmail = AppConfig.get("smtp.email");
        String fromPassword = AppConfig.get("smtp.password");
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", String.valueOf(SMTP_PORT));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(fromEmail, fromPassword);
            }
        });
    }

    /* ───────── OTP email ───────── */

    public static void sendOtpEmail(String toEmail, String firstName, String otpCode)
            throws MessagingException, UnsupportedEncodingException {
        String fromEmail = AppConfig.get("smtp.email");
        Session session = buildSession();

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromEmail, "SynergyGig"));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
        message.setSubject("SynergyGig \u2014 Password Reset Code");
        message.setContent(buildOtpHtml(firstName, otpCode), "text/html; charset=utf-8");

        Transport.send(message);
        System.out.println("\u2705 OTP email sent to " + toEmail);
    }

    /* ───────── Verification email ───────── */

    public static void sendVerificationEmail(String toEmail, String firstName, String verifyUrl)
            throws MessagingException, UnsupportedEncodingException {
        String fromEmail = AppConfig.get("smtp.email");
        Session session = buildSession();

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromEmail, "SynergyGig"));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
        message.setSubject("SynergyGig \u2014 Verify Your Email");
        message.setContent(buildVerifyHtml(firstName, verifyUrl), "text/html; charset=utf-8");

        Transport.send(message);
        System.out.println("\u2705 Verification email sent to " + toEmail);
    }

    /* ───────── HTML builders ───────── */

    private static String buildOtpHtml(String firstName, String otpCode) {
        return """
        <div style="font-family:'Segoe UI',Arial,sans-serif;max-width:520px;margin:0 auto;
                     background:#0A090C;border-radius:16px;border:1px solid #1C1B22;padding:0;">
            <div style="background:linear-gradient(90deg,#07393C,#2C666E,#07393C);height:4px;border-radius:16px 16px 0 0;"></div>
            <div style="padding:36px 36px 32px;">
                <div style="text-align:center;margin-bottom:28px;">
                    <h1 style="color:#F0EDEE;font-size:24px;margin:0;font-weight:700;">SynergyGig</h1>
                    <p style="color:#6B6B78;font-size:13px;margin:6px 0 0;">Password Reset Request</p>
                </div>
                <p style="color:#9E9EA8;font-size:14px;line-height:1.6;margin:0 0 20px;">
                    Hi <strong style="color:#90DDF0;">{{FIRST_NAME}}</strong>,<br>
                    We received a request to reset your password. Use the code below:
                </p>
                <div style="background:linear-gradient(135deg,#07393C,#2C666E);border-radius:12px;
                            padding:28px;text-align:center;margin-bottom:24px;">
                    <p style="color:#90DDF0;font-size:11px;margin:0 0 10px;letter-spacing:2px;
                              text-transform:uppercase;font-weight:600;">Verification Code</p>
                    <h2 style="color:#F0EDEE;font-size:40px;letter-spacing:14px;margin:0;
                               font-weight:700;font-family:'Consolas','Courier New',monospace;">{{OTP_CODE}}</h2>
                </div>
                <div style="background:#14131A;border-radius:8px;padding:14px 18px;margin-bottom:24px;
                            border-left:3px solid #2C666E;">
                    <p style="color:#9E9EA8;font-size:12px;margin:0;line-height:1.5;">
                        This code expires in <strong style="color:#90DDF0;">1 minute</strong>.<br>
                        If you didn't request this, you can safely ignore this email.
                    </p>
                </div>
                <p style="color:#6B6B78;font-size:11px;text-align:center;margin:0;">
                    &copy; 2026 SynergyGig &middot; Secure HR Platform
                </p>
            </div>
        </div>
        """.replace("{{FIRST_NAME}}", firstName).replace("{{OTP_CODE}}", otpCode);
    }

    private static String buildVerifyHtml(String firstName, String verifyUrl) {
        return """
        <div style="font-family:'Segoe UI',Arial,sans-serif;max-width:520px;margin:0 auto;
                     background:#0A090C;border-radius:16px;border:1px solid #1C1B22;padding:0;">
            <div style="background:linear-gradient(90deg,#07393C,#2C666E,#07393C);height:4px;border-radius:16px 16px 0 0;"></div>
            <div style="padding:36px 36px 32px;">
                <div style="text-align:center;margin-bottom:28px;">
                    <h1 style="color:#F0EDEE;font-size:24px;margin:0;font-weight:700;">SynergyGig</h1>
                    <p style="color:#6B6B78;font-size:13px;margin:6px 0 0;">Email Verification</p>
                </div>
                <p style="color:#9E9EA8;font-size:14px;line-height:1.6;margin:0 0 20px;">
                    Hi <strong style="color:#90DDF0;">{{FIRST_NAME}}</strong>,<br>
                    Welcome to SynergyGig! Please verify your email to activate your account.
                </p>
                <div style="text-align:center;margin-bottom:24px;">
                    <a href="{{VERIFY_URL}}"
                       style="display:inline-block;background:linear-gradient(135deg,#07393C,#2C666E);
                              color:#F0EDEE;padding:14px 40px;border-radius:10px;text-decoration:none;
                              font-weight:700;font-size:15px;letter-spacing:0.5px;">
                        Verify My Email &rarr;
                    </a>
                </div>
                <div style="background:#14131A;border-radius:8px;padding:14px 18px;margin-bottom:24px;
                            border-left:3px solid #2C666E;">
                    <p style="color:#9E9EA8;font-size:12px;margin:0;line-height:1.5;">
                        If you didn't create an account, you can safely ignore this email.
                    </p>
                </div>
                <p style="color:#6B6B78;font-size:11px;text-align:center;margin:0;">
                    &copy; 2026 SynergyGig &middot; Secure HR Platform
                </p>
            </div>
        </div>
        """.replace("{{FIRST_NAME}}", firstName).replace("{{VERIFY_URL}}", verifyUrl);
    }
}
