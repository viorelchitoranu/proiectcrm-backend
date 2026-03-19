package com.springapp.proiectcrm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configurare WebSocket cu STOMP (Simple Text Oriented Messaging Protocol).
 *
 * Arhitectura aleasă: WebSocket + STOMP cu SimpleBroker (in-memory).
 * Nu folosim un broker extern (RabbitMQ, ActiveMQ) deoarece aplicația are
 * un singur nod — SimpleBroker este suficient și nu adaugă dependințe externe.
 *
 * Cum funcționează autentificarea:
 *   Aplicația folosește sesiuni HTTP (HttpSessionSecurityContextRepository).
 *   SockJS face o cerere HTTP inițială la /ws/info pentru negocierea conexiunii.
 *   Această cerere HTTP transportă cookie-ul JSESSIONID → Spring Security
 *   recunoaște automat utilizatorul logat pe conexiunea WebSocket.
 *   NU este nevoie de token suplimentar pentru WebSocket.
 *
 * Fluxul unui mesaj:
 *   Frontend → STOMP SEND /app/board/publish → MessageBoardController.publish()
 *   MessageBoardController → SimpMessageTemplate.convertAndSend(/topic/board/{channel})
 *   SimpleBroker → broadcast tuturor subscribers pe /topic/board/{channel}
 *   Frontend ← STOMP MESSAGE de la broker
 *
 * CORS pe WebSocket:
 *   setAllowedOrigins e setat din aceeași proprietate ca CORS-ul HTTP.
 *   Fără asta, browserul blochează conexiunea WebSocket de pe alt origin.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // Aceleași origini permise ca pentru CORS HTTP — consistență
    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    /**
     * Configurează broker-ul de mesaje.
     *
     * SimpleBroker la /topic — broker in-memory care gestionează subscriptions.
     * Prefixul /topic este standardul STOMP pentru mesaje broadcast (publish-subscribe).
     * Un utilizator abonati la /topic/board/GENERAL primește TOATE mesajele din GENERAL.
     *
     * ApplicationDestinationPrefixes /app — prefix pentru mesajele TRIMISE de client.
     * /app/board/publish → rutează la @MessageMapping("/board/publish") din controller.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // SimpleBroker: broker in-memory, suficient pentru un singur nod
        registry.enableSimpleBroker("/topic");

        // Prefixul /app → mesaje destinate aplicației (procesate de @MessageMapping)
        registry.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Înregistrează endpoint-ul WebSocket cu fallback SockJS.
     *
     * SockJS fallback: dacă browserul sau proxy-ul nu suportă WebSocket nativ,
     * SockJS cade automat pe long-polling HTTP. Asigură compatibilitate maximă.
     *
     * Endpoint: /ws — folosit de frontend ca URL de conectare:
     *   new SockJS('/ws')  sau  new Client({ brokerURL: 'ws://...', webSocketFactory: () => new SockJS('/ws') })
     *
     * ATENTIE: SecurityConfig trebuie să permită /ws/** (adăugat acolo).
     * SockJS face cereri HTTP la /ws/info și /ws/{serverId}/{sessionId}/... în timpul
     * negocierii — toate acestea trebuie să fie autentificate.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry
                .addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                // withSockJS: fallback pentru browsere/proxy-uri fără suport WebSocket nativ
                .withSockJS();
    }
}
