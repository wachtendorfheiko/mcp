package dev.wachten.mcp.ollama;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    private final ChatClient chatClient;

    // Store ChatMemory instances, keyed by conversation ID
    // Using ConcurrentHashMap for basic thread safety in a multi-user scenario
    private ConcurrentHashMap<String, ChatMemory> chatMemories = new ConcurrentHashMap<>();


    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }


    /**
     * Endpunkt für einfache Chat-Anfragen.
     * Beispiel-URL: http://localhost:8080/chat?message=Was ist die Hauptstadt von Frankreich?
     *
     * @param message Die Benutzernachricht an das KI-Modell.
     * @return Die Antwort des KI-Modells als String.
     */
    @GetMapping("/chat")
    public String chat(@RequestParam(value = "message", defaultValue = "Erzähle mir einen Witz") String message) {
        // Erstellt einen Prompt aus der Benutzernachricht.
        Prompt prompt = new Prompt(message);


        return chatClient.prompt(prompt).call().content();
    }

    /**
     * A simple record to represent the chat response, including the message content
     * and the conversation ID.
     *
     * @param message The AI's response message.
     * @param chatId  The ID of the conversation, either provided by the user or generated.
     */
    public record ChatResponse(String message, String chatId) {
    }


    /**
     * Endpunkt für Chat-Anfragen mit einem System-Prompt.
     * Ein System-Prompt gibt dem Modell Anweisungen über seine Rolle oder sein Verhalten.
     * Beispiel-URL: http://localhost:8080/chat-system?message=Wer bist du?
     *
     * @param message Die Benutzernachricht.
     * @return Die Antwort des KI-Modells.
     */
    @GetMapping("/chat-system")
    public String chatWithSystemPrompt(@RequestParam(value = "message", defaultValue = "Was ist das Wetter heute?") String message) {
        // Definiert einen System-Prompt, der die Rolle des Assistenten festlegt.
        // Die Nachrichtenliste ermöglicht es, System- und Benutzerprompts zu kombinieren.
        SystemMessage systemMessage = new SystemMessage("""
                Du bist ein hilfsbereiter und freundlicher KI-Assistent, der präzise und nützliche Informationen liefert.
                """);
        UserMessage userMessage = new UserMessage(message);

        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));


        return chatClient.prompt(prompt).call().content();
    }
}