package com.prabhix.platform.mail.inbound;

import lombok.Getter;
import lombok.Setter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import jakarta.mail.Address;
import jakarta.mail.Header;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Parses raw MIME into structured parts; sanitises HTML for safe rendering. */
@Component
public class MimeParser {

    private static final int SNIPPET_MAX = 320;
    private static final Safelist HTML_SAFELIST = Safelist.relaxed()
            .addTags("img")
            .addAttributes("img", "src", "width", "height", "alt");

    public ParsedMime parse(byte[] rawBytes) throws MessagingException, IOException {
        Session session = Session.getInstance(new Properties());
        MimeMessage message = new MimeMessage(session, new ByteArrayInputStream(rawBytes));
        ParsedMime parsed = new ParsedMime();
        parsed.setMessageIdHeader(normaliseMessageId(message.getMessageID()));
        parsed.setInReplyTo(trimHeader(message.getHeader("In-Reply-To", null)));
        parsed.setReferencesHeader(joinReferences(message));
        parsed.setSubject(message.getSubject());
        parsed.setFrom(extractFrom(message));
        parsed.setFromName(extractFromName(message));
        parsed.setToAddresses(extractAddresses(message.getRecipients(Message.RecipientType.TO)));
        parsed.setCcAddresses(extractAddresses(message.getRecipients(Message.RecipientType.CC)));
        parsed.setReplyToAddress(extractSingleAddress(message.getReplyTo()));
        parsed.setHeaders(extractHeaders(message));
        parsed.setSizeBytes(rawBytes.length);

        List<AttachmentPart> attachments = new ArrayList<>();
        extractContent(message, parsed, attachments);
        parsed.setAttachments(attachments);
        parsed.setAttachmentCount(attachments.stream().filter(a -> !a.isInline()).mapToInt(a -> 1).sum());

        if (parsed.getBodyText() == null && parsed.getBodyHtml() != null) {
            parsed.setBodyText(htmlToText(parsed.getBodyHtml()));
        }
        parsed.setSnippet(buildSnippet(parsed.getBodyText()));
        return parsed;
    }

    private void extractContent(Part part, ParsedMime parsed, List<AttachmentPart> attachments)
            throws MessagingException, IOException {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                extractContent(multipart.getBodyPart(i), parsed, attachments);
            }
            return;
        }

        String disposition = part.getDisposition();
        String fileName = part.getFileName();
        String contentId = null;
        String[] cids = part.getHeader("Content-ID");
        if (cids != null && cids.length > 0) {
            contentId = cids[0].replaceAll("[<>]", "");
        }

        boolean inline = Part.INLINE.equalsIgnoreCase(disposition)
                || (contentId != null && !contentId.isBlank());

        if (part.isMimeType("text/plain") && parsed.getBodyText() == null && !inline) {
            parsed.setBodyText(readText(part));
        } else if (part.isMimeType("text/html") && parsed.getBodyHtml() == null && !inline) {
            parsed.setBodyHtml(sanitizeHtml(readText(part)));
        } else if (shouldTreatAsAttachment(part, disposition, fileName)) {
            AttachmentPart attachment = new AttachmentPart();
            attachment.setInline(inline || contentId != null);
            attachment.setContentId(contentId);
            attachment.setFilename(decodeFilename(fileName));
            attachment.setContentType(part.getContentType());
            attachment.setContent(readBytes(part));
            attachments.add(attachment);
        }
    }

    private boolean shouldTreatAsAttachment(Part part, String disposition, String fileName)
            throws MessagingException {
        if (Part.ATTACHMENT.equalsIgnoreCase(disposition)) {
            return true;
        }
        if (fileName != null && !fileName.isBlank()) {
            return true;
        }
        return !part.isMimeType("text/plain") && !part.isMimeType("text/html");
    }

    static String sanitizeHtml(String html) {
        if (html == null || html.isBlank()) {
            return html;
        }
        Document doc = Jsoup.parse(html);
        doc.select("script, iframe").remove();
        doc.select("*").forEach(el -> {
            el.attributes().asList().forEach(attr -> {
                if (attr.getKey().toLowerCase().startsWith("on")) {
                    el.removeAttr(attr.getKey());
                }
            });
        });
        return Jsoup.clean(doc.body().html(), HTML_SAFELIST);
    }

    public static String htmlToText(String html) {
        return Jsoup.parse(html).text();
    }

    private String readText(Part part) throws MessagingException, IOException {
        Object content = part.getContent();
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof InputStream in) {
            return new String(in.readAllBytes(), detectCharset(part));
        }
        return null;
    }

    private byte[] readBytes(Part part) throws MessagingException, IOException {
        Object content = part.getContent();
        if (content instanceof InputStream in) {
            return in.readAllBytes();
        }
        if (content instanceof byte[] bytes) {
            return bytes;
        }
        if (content instanceof String text) {
            return text.getBytes(detectCharset(part));
        }
        return new byte[0];
    }

    private Charset detectCharset(Part part) throws MessagingException {
        String contentType = part.getContentType();
        if (contentType != null) {
            int idx = contentType.toLowerCase().indexOf("charset=");
            if (idx >= 0) {
                String charset = contentType.substring(idx + 8).trim();
                charset = charset.split("[;\\s]")[0].replace("\"", "");
                try {
                    return Charset.forName(charset);
                } catch (Exception ignored) {
                    // fall through
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private String decodeFilename(String fileName) {
        if (fileName == null) {
            return "attachment";
        }
        try {
            return MimeUtility.decodeText(fileName);
        } catch (Exception ex) {
            return fileName;
        }
    }

    private String buildSnippet(String text) {
        if (text == null) {
            return null;
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= SNIPPET_MAX ? oneLine : oneLine.substring(0, SNIPPET_MAX);
    }

    private Map<String, String> extractHeaders(MimeMessage message) throws MessagingException {
        Map<String, String> headers = new HashMap<>();
        Enumeration<Header> all = message.getAllHeaders();
        while (all.hasMoreElements()) {
            Header h = all.nextElement();
            headers.put(h.getName(), h.getValue());
        }
        return headers;
    }

    private String joinReferences(MimeMessage message) throws MessagingException {
        String[] refs = message.getHeader("References");
        if (refs == null || refs.length == 0) {
            return null;
        }
        return String.join(" ", refs);
    }

    private String extractFrom(MimeMessage message) throws MessagingException {
        Address[] from = message.getFrom();
        if (from == null || from.length == 0) {
            return null;
        }
        if (from[0] instanceof InternetAddress ia) {
            return ia.getAddress();
        }
        return from[0].toString();
    }

    private String extractFromName(MimeMessage message) throws MessagingException {
        Address[] from = message.getFrom();
        if (from == null || from.length == 0) {
            return null;
        }
        if (from[0] instanceof InternetAddress ia) {
            return ia.getPersonal();
        }
        return null;
    }

    private List<String> extractAddresses(Address[] addresses) {
        if (addresses == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Address address : addresses) {
            if (address instanceof InternetAddress ia) {
                result.add(ia.getAddress());
            } else {
                result.add(address.toString());
            }
        }
        return result;
    }

    private String extractSingleAddress(Address[] addresses) {
        List<String> list = extractAddresses(addresses);
        return list.isEmpty() ? null : list.get(0);
    }

    static String normaliseMessageId(String messageId) {
        if (messageId == null) {
            return null;
        }
        return messageId.trim().replaceAll("^<|>$", "");
    }

    static String trimHeader(String value) {
        if (value == null) {
            return null;
        }
        return normaliseMessageId(value);
    }

    static List<String> parseReferenceIds(String inReplyTo, String references) {
        List<String> ids = new ArrayList<>();
        if (inReplyTo != null && !inReplyTo.isBlank()) {
            ids.add(normaliseMessageId(inReplyTo));
        }
        if (references != null) {
            for (String part : references.split("\\s+")) {
                if (!part.isBlank()) {
                    ids.add(normaliseMessageId(part));
                }
            }
        }
        return ids;
    }

    @Getter
    @Setter
    public static class ParsedMime {
        private String messageIdHeader;
        private String inReplyTo;
        private String referencesHeader;
        private String subject;
        private String from;
        private String fromName;
        private List<String> toAddresses = List.of();
        private List<String> ccAddresses = List.of();
        private String replyToAddress;
        private String bodyText;
        private String bodyHtml;
        private String snippet;
        private Map<String, String> headers = Map.of();
        private int sizeBytes;
        private int attachmentCount;
        private List<AttachmentPart> attachments = List.of();
    }

    @Getter
    @Setter
    public static class AttachmentPart {
        private String filename;
        private String contentType;
        private byte[] content;
        private boolean inline;
        private String contentId;
    }
}
