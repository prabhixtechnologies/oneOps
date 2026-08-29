package com.prabhix.platform.org.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 * Reads TXT records, for domain-ownership verification.
 *
 * <p>JNDI's DNS provider rather than a library. It is in the JDK, it is what {@code InetAddress} uses
 * underneath, and this needs one record type with no zone transfers or DNSSEC validation. A DNS
 * library would be a dependency carrying a parser for a protocol we barely touch.
 *
 * <p>Extracted into its own class so the verification service can be tested without a network. The
 * alternative — a test that resolves a real domain — passes or fails on whether the build machine has
 * working DNS, which is not what any of those tests are about.
 */
@Slf4j
@Component
public class DnsTxtLookup {

    /**
     * Every TXT value at this name, or an empty list if there are none.
     *
     * <p>A missing name and a name with no TXT records are the same answer here: the record we are
     * looking for is not published. Distinguishing them would give the caller a difference it has no
     * use for.
     *
     * @throws DnsUnavailableException if the resolver could not be reached, which is emphatically not
     *     the same as "the record is absent" — treating a resolver outage as a failed check would
     *     un-verify every domain on the platform during it
     */
    public List<String> txt(String name) {
        Hashtable<String, String> environment = new Hashtable<>();
        environment.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        // Without these, a domain whose nameservers are unreachable hangs the request thread for the
        // provider's default, which is a minute. One retry, because UDP loses packets.
        environment.put("com.sun.jndi.dns.timeout.initial", "3000");
        environment.put("com.sun.jndi.dns.timeout.retries", "2");

        InitialDirContext context = null;
        try {
            context = new InitialDirContext(environment);
            Attributes attributes = context.getAttributes(name, new String[]{"TXT"});
            Attribute txt = attributes.get("TXT");
            if (txt == null) {
                return List.of();
            }

            List<String> values = new ArrayList<>(txt.size());
            for (int i = 0; i < txt.size(); i++) {
                values.add(unquote(String.valueOf(txt.get(i))));
            }
            return values;
        } catch (NamingException ex) {
            // JNDI reports "name not found" as a NamingException too, and there is no reliable typed
            // distinction across providers. The message is the only signal available, so anything that
            // is not clearly a missing name is treated as the resolver being unavailable — failing
            // closed on the side that does not revoke anybody's verification.
            String message = String.valueOf(ex.getMessage());
            if (message.contains("Name not found") || message.contains("DNS name not found")
                    || message.contains("NXDOMAIN")) {
                return List.of();
            }
            log.warn("DNS lookup for {} failed: {}", name, message);
            throw new DnsUnavailableException(message);
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException ignored) {
                    // Closing a context that already failed is not worth reporting.
                }
            }
        }
    }

    /**
     * A TXT record longer than 255 bytes arrives as several quoted strings that the resolver joins,
     * so the raw value can contain embedded quotes and spaces. Verification compares the whole value,
     * so those have to go.
     */
    private static String unquote(String value) {
        return value.replace("\"", "").trim();
    }

    /** The resolver could not answer. Distinct from "the record is not there". */
    public static class DnsUnavailableException extends RuntimeException {
        public DnsUnavailableException(String message) {
            super(message);
        }
    }
}
