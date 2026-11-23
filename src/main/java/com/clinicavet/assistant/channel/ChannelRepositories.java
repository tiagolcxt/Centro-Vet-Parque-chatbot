package com.clinicavet.assistant.channel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repositórios JPA do canal. Filtrados pela clínica atual ({@code @TenantId}). */
final class ChannelRepositories {

    private ChannelRepositories() {
    }
}

interface ChannelAccountJpaRepository extends JpaRepository<ChannelAccountEntity, UUID> {

    Optional<ChannelAccountEntity> findFirstByProvider(String provider);
}

interface OutboundMessageJpaRepository extends JpaRepository<OutboundMessageEntity, UUID> {

    Optional<OutboundMessageEntity> findFirstByWamid(String wamid);

    Optional<OutboundMessageEntity> findByMessageId(UUID messageId);

    List<OutboundMessageEntity> findByMessageIdIn(Collection<UUID> messageIds);
}
