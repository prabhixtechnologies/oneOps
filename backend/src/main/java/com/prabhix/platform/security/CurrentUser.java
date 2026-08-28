package com.prabhix.platform.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the authenticated {@link PrabhixPrincipal} into a controller method.
 *
 * <pre>{@code
 * @GetMapping("/me")
 * public UserDto me(@CurrentUser PrabhixPrincipal principal) { ... }
 * }</pre>
 *
 * <p>Resolves to {@code null} on a public endpoint with no credentials, so a parameter on a
 * public route must be null-checked. On an authenticated route it is always present.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
