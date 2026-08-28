package com.prabhix.platform.mail.provisioning;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.Mailbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AES-GCM encryption for mailbox IMAP/SMTP passwords.
 *
 * <p>Values written after this cipher exists are prefixed with {@code v1:}. Rows that predate
 * encryption store the password verbatim; on read we attempt decryption and fall back to the
 * stored string when it is not a {@code v1:} blob.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailboxCredentialsCipher {

    private static final String PREFIX = "v1:";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final PrabhixProperties properties;
    private final Set<String> legacyWarned = ConcurrentHashMap.newKeySet();

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception ex) {
            throw new IllegalStateException("Mailbox credential encryption failed", ex);
        }
    }

    public void storeImapPassword(Mailbox mailbox, String plaintext) {
        mailbox.setImapPasswordEnc(encrypt(plaintext));
    }

    public void storeSmtpPassword(Mailbox mailbox, String plaintext) {
        mailbox.setSmtpPasswordEnc(encrypt(plaintext));
    }

    public String readImapPassword(Mailbox mailbox) {
        return decrypt(mailbox.getImapPasswordEnc(), "imap:" + mailbox.getId());
    }

    public String readSmtpPassword(Mailbox mailbox) {
        return decrypt(mailbox.getSmtpPasswordEnc(), "smtp:" + mailbox.getId());
    }

    public String decrypt(String stored, String legacyContextKey) {
        if (stored == null || stored.isBlank()) {
            return stored;
        }
        if (!stored.startsWith(PREFIX)) {
            warnLegacyOnce(legacyContextKey);
            return stored;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (AEADBadTagException ex) {
            throw ApiException.of(ErrorCode.MAIL_CREDENTIALS_UNREADABLE,
                    "Stored mailbox credentials are corrupt");
        } catch (IllegalArgumentException ex) {
            throw ApiException.of(ErrorCode.MAIL_CREDENTIALS_UNREADABLE,
                    "Stored mailbox credentials are corrupt");
        } catch (Exception ex) {
            throw ApiException.of(ErrorCode.MAIL_CREDENTIALS_UNREADABLE,
                    "Stored mailbox credentials are corrupt", ex);
        }
    }

    private void warnLegacyOnce(String contextKey) {
        if (contextKey != null && legacyWarned.add(contextKey)) {
            log.warn("Mailbox {} uses unencrypted legacy credentials; re-save to encrypt", contextKey);
        }
    }

    private SecretKey deriveKey() throws Exception {
        String secret = properties.mail().credentials().secret();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] key = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key, "AES");
    }
}
