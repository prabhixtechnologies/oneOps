package com.prabhix.platform.mail.outbound;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.mail.domain.MailTemplate;
import com.prabhix.platform.mail.repository.MailTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateRendererTest {

    @Mock
    MailTemplateRepository templateRepository;

    TemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new TemplateRenderer(templateRepository);
    }

    @Test
    void missingRequiredVariableThrows() {
        MailTemplate template = template(
                "[(${name})]",
                "<p th:text=\"${name}\">x</p>",
                "[{\"name\":\"name\",\"required\":true}]");
        when(templateRepository.resolveTemplate(any(), any(), any())).thenReturn(List.of(template));

        ApiException ex = assertThrows(ApiException.class,
                () -> renderer.render("test", "en", UUID.randomUUID(), Map.of()));
        assertEquals(ErrorCode.MAIL_TEMPLATE_RENDER_FAILED, ex.getCode());
    }

    @Test
    void derivesPlainTextFromHtml() {
        // TemplateRenderer delegates plain-text derivation to MimeParser when body_text is null.
        String text = com.prabhix.platform.mail.inbound.MimeParser.htmlToText(
                "<p>Hello <b>world</b></p>");
        assertTrue(text.contains("Hello world"));
    }

    private MailTemplate template(String subject, String html, String variables) {
        MailTemplate t = new MailTemplate();
        t.setSubject(subject);
        t.setBodyHtml(html);
        t.setVariables(variables);
        return t;
    }
}
