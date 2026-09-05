package com.example.financialemail.service;

import com.example.financialemail.api.InvalidEmailFileException;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

@Component
public class EmlParser {
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_EXTRACTED_CHARACTERS = 200_000;
    private static final int MAX_MIME_DEPTH = 30;
    private static final int MAX_MIME_PARTS = 500;
    private static final List<DateTimeFormatter> RECEIVED_DATE_FORMATS = List.of(
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ofPattern("EEE, d MMM uuuu HH:mm:ss xx", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM uuuu HH:mm:ss xx", Locale.ENGLISH));
    private final Clock clock;

    public EmlParser(Clock clock) {
        this.clock = clock;
    }

    public ParsedEmail parse(MultipartFile file) {
        validate(file);

        try (InputStream inputStream = file.getInputStream()) {
            MimeMessage message = new MimeMessage(Session.getInstance(new Properties()), inputStream);
            BodyContent bodyContent = new BodyContent();
            collectBody(message, bodyContent, 0, new PartCounter());

            String body = bodyContent.preferredText();
            if (body.isBlank()) {
                throw new InvalidEmailFileException("The .eml file does not contain a readable message body");
            }

            String subject = message.getSubject();
            String extracted = subject == null || subject.isBlank()
                    ? body
                    : "Subject: " + subject.strip() + "\n\n" + body;
            if (extracted.length() > MAX_EXTRACTED_CHARACTERS) {
                throw new InvalidEmailFileException("Extracted email content must not exceed 200,000 characters");
            }
            return new ParsedEmail(extracted.strip(), resolveReceptionDate(message));
        } catch (InvalidEmailFileException exception) {
            throw exception;
        } catch (MessagingException | IOException | RuntimeException exception) {
            throw new InvalidEmailFileException("The uploaded file is not a readable .eml message", exception);
        }
    }

    private LocalDate resolveReceptionDate(MimeMessage message) throws MessagingException {
        String[] receivedHeaders = message.getHeader("Received");
        if (receivedHeaders != null) {
            for (String header : receivedHeaders) {
                LocalDate parsed = parseReceivedHeader(header);
                if (parsed != null) {
                    return parsed;
                }
            }
        }

        Date sentDate = message.getSentDate();
        if (sentDate != null) {
            return sentDate.toInstant().atZone(clock.getZone()).toLocalDate();
        }
        return LocalDate.now(clock);
    }

    private LocalDate parseReceivedHeader(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        int separator = header.lastIndexOf(';');
        if (separator < 0 || separator == header.length() - 1) {
            return null;
        }

        String value = header.substring(separator + 1)
                .replaceFirst("\\s+\\([^()]*\\)\\s*$", "")
                .strip();
        for (DateTimeFormatter formatter : RECEIVED_DATE_FORMATS) {
            try {
                return OffsetDateTime.parse(value, formatter).toLocalDate();
            } catch (DateTimeParseException ignored) {
                // Try the next common RFC 5322 date representation.
            }
        }
        return null;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidEmailFileException("A non-empty .eml file is required");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".eml")) {
            throw new InvalidEmailFileException("Only .eml files are supported");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new InvalidEmailFileException("The .eml file must not exceed 10 MB");
        }
    }

    private void collectBody(Part part, BodyContent bodyContent, int depth, PartCounter counter)
            throws MessagingException, IOException {
        if (depth > MAX_MIME_DEPTH) {
            throw new InvalidEmailFileException("The .eml MIME structure is nested too deeply");
        }
        if (++counter.count > MAX_MIME_PARTS) {
            throw new InvalidEmailFileException("The .eml message contains too many MIME parts");
        }
        String disposition = part.getDisposition();
        if (Part.ATTACHMENT.equalsIgnoreCase(disposition)
                || (part.getFileName() != null && !Part.INLINE.equalsIgnoreCase(disposition))) {
            return;
        }
        if (part.isMimeType("text/plain")) {
            addTextContent(part, bodyContent.plainText);
            return;
        }
        if (part.isMimeType("text/html")) {
            Object content = part.getContent();
            if (content instanceof String html) {
                bodyContent.htmlText.add(htmlToText(html));
            }
            return;
        }
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (!(content instanceof Multipart multipart)) {
                throw new InvalidEmailFileException("A multipart MIME section could not be decoded");
            }
            for (int index = 0; index < multipart.getCount(); index++) {
                BodyPart bodyPart = multipart.getBodyPart(index);
                collectBody(bodyPart, bodyContent, depth + 1, counter);
            }
            return;
        }
        if (part.isMimeType("message/rfc822")) {
            Object nestedMessage = part.getContent();
            if (nestedMessage instanceof Message message) {
                collectBody(message, bodyContent, depth + 1, counter);
            }
        }
    }

    private void addTextContent(Part part, List<String> destination)
            throws MessagingException, IOException {
        Object content = part.getContent();
        if (content instanceof String text) {
            destination.add(text);
        }
    }

    private String htmlToText(String html) {
        return html
                .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?i)<br\\s*/?>|</p\\s*>|</div\\s*>|</li\\s*>", "\n")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n\\s*\\n+", "\n\n")
                .strip();
    }

    private static final class BodyContent {
        private final List<String> plainText = new ArrayList<>();
        private final List<String> htmlText = new ArrayList<>();

        private String preferredText() {
            List<String> preferred = plainText.isEmpty() ? htmlText : plainText;
            return preferred.stream()
                    .filter(text -> text != null && !text.isBlank())
                    .map(String::strip)
                    .reduce((first, second) -> first + "\n\n" + second)
                    .orElse("");
        }
    }

    private static final class PartCounter {
        private int count;
    }
}
