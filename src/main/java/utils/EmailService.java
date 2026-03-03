package utils;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.*;
import java.io.File;
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

    /* ───────── Contract email with PDF attachment ───────── */

    /**
     * Sends a personalized contract email with the PDF contract attached.
     *
     * @param toEmail        Recipient email
     * @param applicantName  First name of the applicant
     * @param ownerName      Name of the employer / contract owner
     * @param offerTitle     Title of the offer
     * @param currency       Currency code
     * @param amount         Contract amount
     * @param blockchainHash The blockchain verification hash
     * @param pdfFile        The generated contract PDF file to attach
     */
    public static void sendContractEmail(String toEmail, String applicantName, String ownerName,
                                          String offerTitle, String currency, double amount,
                                          String blockchainHash, File pdfFile)
            throws MessagingException, UnsupportedEncodingException {
        String fromEmail = AppConfig.get("smtp.email");
        Session session = buildSession();

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromEmail, "SynergyGig"));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
        message.setSubject("SynergyGig \u2014 Your Contract for \"" + offerTitle + "\" is Ready!");

        // Build multipart: HTML body + PDF attachment
        MimeMultipart multipart = new MimeMultipart();

        // Part 1: HTML body
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(buildContractHtml(applicantName, ownerName, offerTitle, currency, amount, blockchainHash), "text/html; charset=utf-8");
        multipart.addBodyPart(htmlPart);

        // Part 2: PDF attachment
        if (pdfFile != null && pdfFile.exists()) {
            MimeBodyPart attachPart = new MimeBodyPart();
            DataSource source = new FileDataSource(pdfFile);
            attachPart.setDataHandler(new DataHandler(source));
            attachPart.setFileName("SynergyGig_Contract_" + offerTitle.replaceAll("[^a-zA-Z0-9]", "_") + ".pdf");
            multipart.addBodyPart(attachPart);
        }

        message.setContent(multipart);
        Transport.send(message);
        System.out.println("\u2705 Contract email sent to " + toEmail + " with PDF attachment");
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

    /* ───────── Contract email HTML ───────── */

    private static String buildContractHtml(String applicantName, String ownerName,
                                             String offerTitle, String currency, double amount,
                                             String blockchainHash) {
        String shortHash = blockchainHash != null && blockchainHash.length() > 16
                ? blockchainHash.substring(0, 8) + "..." + blockchainHash.substring(blockchainHash.length() - 8)
                : (blockchainHash != null ? blockchainHash : "N/A");
        String qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=160x160&data="
                + (blockchainHash != null ? blockchainHash : "none");

        return """
        <div style="font-family:'Segoe UI',Arial,sans-serif;max-width:600px;margin:0 auto;
                     background:#0A090C;border-radius:16px;border:1px solid #1C1B22;padding:0;">
            <!-- Top accent bar -->
            <div style="background:linear-gradient(90deg,#07393C,#2C666E,#B49932,#2C666E,#07393C);height:5px;border-radius:16px 16px 0 0;"></div>

            <div style="padding:36px 36px 32px;">
                <!-- Logo -->
                <div style="text-align:center;margin-bottom:28px;">
                    <h1 style="color:#90DDF0;font-size:28px;margin:0;font-weight:700;letter-spacing:2px;">SYNERGYGIG</h1>
                    <p style="color:#6B6B78;font-size:13px;margin:6px 0 0;">Secure HR Platform &bull; Blockchain Verified</p>
                </div>

                <!-- Greeting -->
                <p style="color:#9E9EA8;font-size:14px;line-height:1.6;margin:0 0 20px;">
                    Dear <strong style="color:#90DDF0;">{{APPLICANT_NAME}}</strong>,<br><br>
                    Congratulations! Your application has been <strong style="color:#22c55e;">accepted</strong>.
                    A contract has been prepared for your review and signature.
                </p>

                <!-- Contract details card -->
                <div style="background:linear-gradient(135deg,#07393C,#2C666E);border-radius:12px;
                            padding:24px;margin-bottom:24px;">
                    <p style="color:#90DDF0;font-size:11px;margin:0 0 14px;letter-spacing:2px;
                              text-transform:uppercase;font-weight:600;">Contract Details</p>
                    <table style="width:100%;border-collapse:collapse;">
                        <tr>
                            <td style="color:#6B6B78;font-size:12px;padding:6px 0;">Position</td>
                            <td style="color:#F0EDEE;font-size:13px;padding:6px 0;font-weight:600;text-align:right;">{{OFFER_TITLE}}</td>
                        </tr>
                        <tr>
                            <td style="color:#6B6B78;font-size:12px;padding:6px 0;">Employer</td>
                            <td style="color:#F0EDEE;font-size:13px;padding:6px 0;text-align:right;">{{OWNER_NAME}}</td>
                        </tr>
                        <tr>
                            <td style="color:#6B6B78;font-size:12px;padding:6px 0;">Compensation</td>
                            <td style="color:#B49932;font-size:14px;padding:6px 0;font-weight:700;text-align:right;">{{CURRENCY}} {{AMOUNT}}</td>
                        </tr>
                    </table>
                </div>

                <!-- QR Code verification section -->
                <div style="background:#14131A;border-radius:12px;padding:20px;margin-bottom:24px;
                            border:1px solid #1C1B22;text-align:center;">
                    <p style="color:#90DDF0;font-size:11px;margin:0 0 12px;letter-spacing:2px;
                              text-transform:uppercase;font-weight:600;">Blockchain Verification</p>
                    <img src="{{QR_URL}}" alt="QR Code" style="width:130px;height:130px;border-radius:8px;
                         border:2px solid #2C666E;margin:0 auto 12px;display:block;" />
                    <p style="color:#6B6B78;font-size:10px;margin:0 0 8px;font-family:'Courier New',monospace;">
                        Hash: {{SHORT_HASH}}
                    </p>
                    <p style="color:#9E9EA8;font-size:11px;margin:0;line-height:1.4;">
                        Scan this QR code to verify contract authenticity.<br>
                        Your HR manager will verify this during onboarding.
                    </p>
                </div>

                <!-- CTA -->
                <div style="background:#14131A;border-radius:8px;padding:16px 18px;margin-bottom:24px;
                            border-left:3px solid #B49932;">
                    <p style="color:#F0EDEE;font-size:13px;margin:0 0 6px;font-weight:600;">
                        \ud83d\udcce Contract PDF Attached
                    </p>
                    <p style="color:#9E9EA8;font-size:12px;margin:0;line-height:1.5;">
                        Please review the attached PDF contract carefully. Sign and return it
                        to your HR manager, or confirm digitally through the SynergyGig platform.
                    </p>
                </div>

                <!-- Divider -->
                <div style="border-top:1px solid #1C1B22;margin:20px 0;"></div>

                <p style="color:#6B6B78;font-size:11px;text-align:center;margin:0;">
                    &copy; 2026 SynergyGig &middot; Secure HR Platform &middot; Blockchain-Secured Contracts
                </p>
            </div>
        </div>
        """.replace("{{APPLICANT_NAME}}", applicantName)
           .replace("{{OWNER_NAME}}", ownerName)
           .replace("{{OFFER_TITLE}}", offerTitle)
           .replace("{{CURRENCY}}", currency)
           .replace("{{AMOUNT}}", String.format("%.2f", amount))
           .replace("{{SHORT_HASH}}", shortHash)
           .replace("{{QR_URL}}", qrUrl);
    }
}
