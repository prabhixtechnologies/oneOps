package com.prabhix.platform.security.rbac;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthorizePermissionConsistencyTest {

    @Test
    void authorizeHasOneConstantPerPermission() throws Exception {
        Set<String> permissionNames = Arrays.stream(Permission.values())
                .map(Permission::name)
                .collect(Collectors.toSet());

        Set<String> authorizeConstants = Arrays.stream(Authorize.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .filter(field -> Modifier.isPublic(field.getModifiers()))
                .filter(field -> field.getType() == String.class)
                .filter(field -> !field.getName().equals("AUTHENTICATED"))
                .map(field -> {
                    try {
                        field.setAccessible(true);
                        String expression = (String) field.get(null);
                        if (expression == null || !expression.startsWith("hasAuthority('")) {
                            return null;
                        }
                        int start = expression.indexOf('\'') + 1;
                        int end = expression.lastIndexOf('\'');
                        if (end <= start) {
                            return null;
                        }
                        return expression.substring(start, end);
                    } catch (IllegalAccessException ex) {
                        throw new RuntimeException(ex);
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        assertEquals(permissionNames, authorizeConstants,
                "Authorize constants must match Permission enum values exactly");
    }
}
