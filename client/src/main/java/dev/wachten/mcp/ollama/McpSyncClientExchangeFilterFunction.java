package dev.wachten.mcp.ollama;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.ClientCredentialsOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;


public class McpSyncClientExchangeFilterFunction implements ExchangeFilterFunction {

    private final ClientCredentialsOAuth2AuthorizedClientProvider clientCredentialTokenProvider = new ClientCredentialsOAuth2AuthorizedClientProvider();

    private final ServletOAuth2AuthorizedClientExchangeFilterFunction delegate;

    private final ClientRegistrationRepository clientRegistrationRepository;

    // Must match registration id in property
    // spring.security.oauth2.client.registration.<REGISTRATION-ID>.authorization-grant-type=authorization_code
    private static final String AUTHORIZATION_CODE_CLIENT_REGISTRATION_ID = "authserver";

    // Must match registration id in property
    // spring.security.oauth2.client.registration.<REGISTRATION-ID>.authorization-grant-type=client_credentials
    private static final String CLIENT_CREDENTIALS_CLIENT_REGISTRATION_ID = "authserver-client-credentials";

    public McpSyncClientExchangeFilterFunction(OAuth2AuthorizedClientManager clientManager,
                                               ClientRegistrationRepository clientRegistrationRepository) {
        this.delegate = new ServletOAuth2AuthorizedClientExchangeFilterFunction(clientManager);
        this.delegate.setDefaultClientRegistrationId(AUTHORIZATION_CODE_CLIENT_REGISTRATION_ID);
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    /**
     * Add an {@code access_token} to the request sent to the MCP server.
     * <p>
     * If we are in the context of a ServletRequest, this means a user is currently
     * involved, and we should add a token on behalf of the user, using the
     * {@code authorization_code} grant. This typically happens when doing an MCP
     * {@code tools/call}.
     * <p>
     * If we are NOT in the context of a ServletRequest, this means we are in the startup
     * phases of the application, where the MCP client is initialized. We use the
     * {@code client_credentials} grant in that case, and add a token on behalf of the
     * application itself.
     */
    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes) {
            return this.delegate.filter(request, next);
        } else {
            var accessToken = getClientCredentialsAccessToken();
            var requestWithToken = ClientRequest.from(request)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .build();
            return next.exchange(requestWithToken);
        }
    }

    private String getClientCredentialsAccessToken() {
        var clientRegistration = this.clientRegistrationRepository
                .findByRegistrationId(CLIENT_CREDENTIALS_CLIENT_REGISTRATION_ID);

        var authRequest = OAuth2AuthorizationContext.withClientRegistration(clientRegistration)
                .principal(new AnonymousAuthenticationToken("client-credentials-client", "client-credentials-client",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")))
                .build();
        return this.clientCredentialTokenProvider.authorize(authRequest).getAccessToken().getTokenValue();
    }

    /**
     * Configure a {@link WebClient} to use this exchange filter function.
     */
    public Consumer<WebClient.Builder> configuration() {
        return builder -> builder.defaultRequest(this.delegate.defaultRequest()).filter(this);
    }

}