package com.prabhix.platform.config;

import com.prabhix.platform.security.PrabhixPrincipal;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    static {
        // Controllers take the caller as a resolved @AuthenticationPrincipal argument. springdoc
        // cannot tell that apart from a real input, so without this every authenticated operation
        // documents PrabhixPrincipal — permissions and all — as something the client must send.
        // Generated clients then carry a bogus required parameter, and the internal permission
        // model leaks into the public contract.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(PrabhixPrincipal.class);
    }

    private final PrabhixProperties properties;

    @Bean
    public OpenAPI prabhixOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Prabhix Platform API")
                        .version("v1")
                        .description("""
                                Multi-tenant API behind the Prabhix console and marketing site.

                                **Tenancy** — every authenticated request acts within one organization.
                                The active organization comes from the `org` claim on the access token;
                                send `X-Prabhix-Org` to pin a specific one you already have access to.

                                **Pagination** — list endpoints are keyset paginated. Pass the opaque
                                `nextCursor` from the previous response back as `cursor`. Treat it as
                                opaque; its format is not part of this contract.

                                **Errors** — every failure returns
                                `{ code, message, fieldErrors?, traceId?, path, timestamp }`.
                                Branch on `code`, never on `message`.
                                """)
                        .contact(new Contact()
                                .name("Prabhix Technologies")
                                .url("https://prabhixtechnologies.com")
                                .email("support@prabhixtechnologies.com"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(
                        new Server().url(properties.urls().api()).description("This instance"),
                        new Server().url("https://api.prabhixtechnologies.com").description("Production")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token from POST /api/v1/oneops/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
