package com.clinicavet.assistant.conversation.api;

import com.clinicavet.assistant.conversation.application.HandleIncomingMessageUseCase;
import com.clinicavet.assistant.conversation.application.OpenConversationUseCase;
import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.conversation.domain.ConversationRepository;
import com.clinicavet.assistant.conversation.domain.MessageRepository;
import com.clinicavet.assistant.shared.domain.Channel;
import com.clinicavet.assistant.shared.domain.ContactIdentity;
import com.clinicavet.assistant.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * Canal API de testes (DM-10). Todas as rotas exigem {@code X-Api-Key} (ADR-0012). Só dados fictícios
 * (DA-17). No canal API, quem chama informa o contato: a chave é o que protege esse vínculo.
 */
@Validated
@RestController
@RequestMapping("/api/conversations")
class ConversationController {

    private final OpenConversationUseCase openConversation;
    private final HandleIncomingMessageUseCase handleMessage;
    private final ConversationRepository conversations;
    private final MessageRepository messages;

    ConversationController(OpenConversationUseCase openConversation, HandleIncomingMessageUseCase handleMessage,
                           ConversationRepository conversations, MessageRepository messages) {
        this.openConversation = openConversation;
        this.handleMessage = handleMessage;
        this.conversations = conversations;
        this.messages = messages;
    }

    /** Devolve a conversa ativa do contato ({@code 200}) ou abre uma nova ({@code 201}) (ADR-0009). */
    @PostMapping
    ResponseEntity<ConversationResponse> open(@Valid @RequestBody OpenConversationRequest body) {
        OpenConversationUseCase.Result result =
                openConversation.open(new ContactIdentity(Channel.API, body.externalContactId()));
        ConversationResponse response = ConversationResponse.from(result.conversation());
        if (result.started()) {
            return ResponseEntity.created(URI.create("/api/conversations/" + response.id())).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/messages")
    TurnResponse sendMessage(@PathVariable UUID id, @Valid @RequestBody SendMessageRequest body) {
        return TurnResponse.from(handleMessage.handle(new ConversationId(id), body.text()));
    }

    /** Histórico completo, em ordem cronológica, paginado. */
    @GetMapping("/{id}/messages")
    MessagePageResponse listMessages(@PathVariable UUID id,
                                     @RequestParam(defaultValue = "0") @Min(0) int page,
                                     @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        ConversationId conversationId = new ConversationId(id);
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversa", id));
        return new MessagePageResponse(
                conversation.id().value(),
                messages.findPage(conversationId, page, size).stream().map(MessageResponse::from).toList(),
                page, size, messages.countByConversation(conversationId));
    }
}
