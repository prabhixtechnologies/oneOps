package com.prabhix.platform.chat.web;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatStreamControllerContractTest {

    @Test
    void publicStreamRequiresChatTokenHeader() throws NoSuchMethodException {
        var method = ChatStreamController.class.getMethod(
                "streamVisitor", UUID.class, UUID.class, String.class);
        boolean headerRequired = Arrays.stream(method.getParameters())
                .anyMatch(parameter -> {
                    RequestHeader header = parameter.getAnnotation(RequestHeader.class);
                    return header != null && "X-Chat-Token".equals(header.value());
                });
        assertThat(headerRequired).isTrue();
        assertThat(Arrays.stream(method.getParameters())
                .noneMatch(parameter -> parameter.isAnnotationPresent(org.springframework.web.bind.annotation.RequestParam.class)
                        && "token".equals(parameter.getName()))).isTrue();
    }
}
