package com.prabhix.platform.mail.provisioning;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.support.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MailboxCredentialsCipherTest {

    MailboxCredentialsCipher cipher;
    Mailbox mailbox;

    @BeforeEach
    void setUp() {
        PrabhixProperties props = TestProperties.defaults();
        cipher = new MailboxCredentialsCipher(props);
        mailbox = new Mailbox();
        mailbox.setId(UUID.randomUUID());
    }

    @Test
    void roundTripEncryptsAndDecrypts() {
        cipher.storeImapPassword(mailbox, "s3cret!");
        assertTrue(mailbox.getImapPasswordEnc().startsWith("v1:"));
        assertEquals("s3cret!", cipher.readImapPassword(mailbox));
    }

    @Test
    void legacyPlaintextIsReturnedWithFallback() {
        mailbox.setImapPasswordEnc("plain-old-password");
        assertEquals("plain-old-password", cipher.readImapPassword(mailbox));
    }

    @Test
    void tamperedCiphertextThrowsUnreadable() {
        cipher.storeSmtpPassword(mailbox, "pw");
        String tampered = mailbox.getSmtpPasswordEnc().substring(0, mailbox.getSmtpPasswordEnc().length() - 4) + "XXXX";
        mailbox.setSmtpPasswordEnc(tampered);
        ApiException ex = assertThrows(ApiException.class, () -> cipher.readSmtpPassword(mailbox));
        assertEquals(ErrorCode.MAIL_CREDENTIALS_UNREADABLE, ex.getCode());
    }
}
