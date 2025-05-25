package dev.wachten.mcp.ollama;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
public class OllamaChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(OllamaChatApplication.class, args);
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder chatClientBuilder, ToolCallbackProvider toolCallbackProvider) {

        return chatClientBuilder
                .defaultToolCallbacks(toolCallbackProvider)
                .build();
    }

    // (Optional) If you have a user-facing application, keep this for OAuth2 Login.
    // If it's purely machine-to-machine, you might not need this filter chain for web requests.
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().permitAll() // Or .authenticated() if you have other secured endpoints
                )
                .oauth2Client(Customizer.withDefaults()) // Enable OAuth2 client functionality

        // If you're purely machine-to-machine, you might disable CSRF and CORS if not needed.
         .csrf(AbstractHttpConfigurer::disable)
         .cors(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) { // Using service for persistence if needed

        // Build a provider that specifically supports client_credentials
        OAuth2AuthorizedClientProvider authorizedClientProvider =
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials() // This is the key for client_credentials grant
                        .build();

        AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrationRepository, authorizedClientService);

        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        return authorizedClientManager;
    }

    // 2. Define the WebClient.Builder that uses the above manager
    @Bean("oauth2WebClientBuilderClientCredentials") // Give it a specific name
    public WebClient.Builder oauth2WebClientBuilderClientCredentials(
            OAuth2AuthorizedClientManager authorizedClientManager) {

        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Client =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);

        // Crucial: Specify the client registration ID for this WebClient to use
        oauth2Client.setDefaultClientRegistrationId("authserver-client-credentials"); // Your client_credentials registration ID

        return WebClient.builder()
                .apply(oauth2Client.oauth2Configuration()); // Apply the filter function
    }


}
