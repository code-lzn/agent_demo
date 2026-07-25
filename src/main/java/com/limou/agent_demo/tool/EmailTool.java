package com.limou.agent_demo.tool;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Email tool: send and read emails via SMTP/IMAP.
 */
@Component
public class EmailTool {

    @Tool(description = "Send an email via SMTP. Provide SMTP server details, credentials, and email content." +
            " For QQ Mail: host=smtp.qq.com, port=587, useSSL=true." +
            " For Gmail: host=smtp.gmail.com, port=587, useSSL=true." +
            " For 163: host=smtp.163.com, port=25, useSSL=false")
    public String sendEmail(
            @ToolParam(description = "SMTP server host, e.g. smtp.qq.com") String smtpHost,
            @ToolParam(description = "SMTP port, e.g. 587 or 25") int smtpPort,
            @ToolParam(description = "Use SSL/TLS? Usually true for port 587, false for port 25") boolean useSSL,
            @ToolParam(description = "Email account username") String username,
            @ToolParam(description = "Email account password (or authorization code)") String password,
            @ToolParam(description = "Sender email address") String from,
            @ToolParam(description = "Recipient email address") String to,
            @ToolParam(description = "Email subject") String subject,
            @ToolParam(description = "Email body (plain text)") String body) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", String.valueOf(smtpPort));
            props.put("mail.smtp.auth", "true");
            if (useSSL) {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.ssl.trust", smtpHost);
            }

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setText(body);

            Transport.send(message);
            return "Email sent successfully to " + to;
        } catch (Exception e) {
            return "Failed to send email: " + e.getMessage();
        }
    }

    @Tool(description = "Read recent unread emails from an IMAP inbox." +
            " For QQ Mail: host=imap.qq.com, port=993, useSSL=true." +
            " Returns sender, subject, and date for the most recent messages")
    public String readEmails(
            @ToolParam(description = "IMAP server host, e.g. imap.qq.com") String imapHost,
            @ToolParam(description = "IMAP port, e.g. 993") int imapPort,
            @ToolParam(description = "Use SSL? Usually true") boolean useSSL,
            @ToolParam(description = "Email account username") String username,
            @ToolParam(description = "Email account password (or authorization code)") String password,
            @ToolParam(description = "Maximum number of emails to fetch") int maxResults) {
        try {
            Properties props = new Properties();
            props.put("mail.store.protocol", "imaps");
            props.put("mail.imaps.host", imapHost);
            props.put("mail.imaps.port", String.valueOf(imapPort));
            props.put("mail.imaps.ssl.enable", String.valueOf(useSSL));

            Session session = Session.getInstance(props);
            Store store = session.getStore("imaps");
            store.connect(imapHost, imapPort, username, password);

            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            int count = Math.min(inbox.getMessageCount(), maxResults);
            Message[] messages = inbox.getMessages(inbox.getMessageCount() - count + 1, inbox.getMessageCount());

            StringBuilder sb = new StringBuilder();
            sb.append("Recent ").append(count).append(" emails:\n");
            // Newest first
            for (int i = messages.length - 1; i >= 0; i--) {
                Message m = messages[i];
                String from = m.getFrom() != null && m.getFrom().length > 0
                        ? m.getFrom()[0].toString() : "Unknown";
                sb.append("- From: ").append(from).append("\n");
                sb.append("  Subject: ").append(m.getSubject()).append("\n");
                sb.append("  Date: ").append(m.getSentDate()).append("\n\n");
            }

            inbox.close(false);
            store.close();
            return sb.toString().stripTrailing();
        } catch (Exception e) {
            return "Failed to read emails: " + e.getMessage();
        }
    }
}