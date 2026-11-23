package com.clinicavet.assistant.platform.application;

import com.clinicavet.assistant.platform.AccountDisabled;
import com.clinicavet.assistant.platform.Accounts;
import com.clinicavet.assistant.platform.AuditLog;
import com.clinicavet.assistant.platform.ClinicId;
import com.clinicavet.assistant.platform.CurrentClinic;
import com.clinicavet.assistant.platform.StaffUser;
import com.clinicavet.assistant.platform.infrastructure.persistence.RoleEntity;
import com.clinicavet.assistant.platform.infrastructure.persistence.RoleJpaRepository;
import com.clinicavet.assistant.platform.infrastructure.persistence.UserAccountEntity;
import com.clinicavet.assistant.platform.infrastructure.persistence.UserAccountJpaRepository;
import com.clinicavet.assistant.shared.exception.ConflictException;
import com.clinicavet.assistant.shared.exception.DomainValidationException;
import com.clinicavet.assistant.shared.exception.InvalidStateTransitionException;
import com.clinicavet.assistant.shared.exception.ResourceNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Contas, login e convites (ADR-0024).
 * <p>
 * O Hibernate fixa a clínica ({@code @TenantId}) quando a sessão abre. Por isso, no login e no aceite do convite, a
 * conta (global) é lida primeiro e o resto roda numa transação aberta <b>depois</b> de definir a clínica da conta.
 * <p>
 * Valores PROVISÓRIOS, sem decisão da clínica: 5 tentativas erradas bloqueiam por 15 minutos; convite vale 72 horas;
 * senha com pelo menos 10 caracteres.
 */
@Service
public class AccountService implements Accounts {

    static final int MAX_FAILED_LOGINS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    static final Duration INVITE_VALIDITY = Duration.ofHours(72);
    static final int MIN_PASSWORD = 10;
    static final int MAX_PASSWORD = 72;   // limite do bcrypt

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserAccountJpaRepository accounts;
    private final RoleJpaRepository roles;
    private final PasswordEncoder passwords;
    private final AuditLog audit;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    /** Hash de referência para gastar o mesmo tempo quando o e-mail não existe. */
    private final String dummyHash;

    AccountService(UserAccountJpaRepository accounts, RoleJpaRepository roles, PasswordEncoder passwords,
                   AuditLog audit, PlatformTransactionManager transactions, Clock clock,
                   ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.roles = roles;
        this.passwords = passwords;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
        this.events = events;
        this.dummyHash = passwords.encode("senha-de-referencia-sem-uso");
    }

    // ---------------------------------------------------------------- login e sessão

    /** @throws BadCredentialsException sempre com a mesma mensagem, qualquer que seja o motivo */
    public StaffUser authenticate(String email, String password) {
        Optional<UserAccountEntity> found = accounts.findByEmail(normalize(email));
        if (found.isEmpty()) {
            passwords.matches(password == null ? "" : password, dummyHash);
            throw failure();
        }
        ClinicId clinic = new ClinicId(found.get().clinicId());
        return inClinic(clinic, () -> {
            UserAccountEntity account = accounts.findById(found.get().id()).orElseThrow(AccountService::failure);
            Instant now = clock.instant();
            boolean locked = account.lockedUntil() != null && account.lockedUntil().isAfter(now);
            boolean matches = account.passwordHash() != null && password != null
                    && passwords.matches(password, account.passwordHash());
            if (locked || account.status() != Status.ACTIVE || !matches) {
                if (!locked && account.status() == Status.ACTIVE) {
                    account.loginFailed(MAX_FAILED_LOGINS, now.plus(LOCK_DURATION));
                }
                audit.record("auth.login_failed", "user", account.id(), locked ? "conta bloqueada" : null);
                return null;
            }
            account.loginSucceeded();
            audit.record("auth.login", "user", account.id(), null);
            return staffUser(account);
        }, true);
    }

    /** Recarrega o funcionário da sessão. Vazio se a conta não estiver mais ativa. */
    public Optional<StaffUser> loadActive(UUID userId, ClinicId clinic) {
        return CurrentClinic.call(clinic, () -> tx.execute(status ->
                accounts.findByIdAndClinicId(userId, clinic.value())
                        .filter(a -> a.status() == Status.ACTIVE)
                        .map(this::staffUser)));
    }

    public void acceptInvite(String token, String newPassword) {
        String password = validPassword(newPassword);
        UserAccountEntity found = accounts.findByInviteTokenHash(sha256(token == null ? "" : token))
                .orElseThrow(() -> new DomainValidationException("token", "convite inválido ou expirado"));
        inClinic(new ClinicId(found.clinicId()), () -> {
            UserAccountEntity account = accounts.findById(found.id()).orElseThrow();
            if (account.inviteExpiresAt() == null || !account.inviteExpiresAt().isAfter(clock.instant())
                    || account.status() == Status.DISABLED) {
                throw new DomainValidationException("token", "convite inválido ou expirado");
            }
            account.acceptInvite(passwords.encode(password));
            audit.record("auth.invite_accepted", "user", account.id(), null);
            return account;
        }, false);
    }

    public void changePassword(StaffUser user, String currentPassword, String newPassword) {
        String password = validPassword(newPassword);
        inClinic(user.clinic(), () -> {
            UserAccountEntity account = accounts.findByIdAndClinicId(user.userId(), user.clinic().value())
                    .orElseThrow(AccountService::failure);
            if (currentPassword == null || !passwords.matches(currentPassword, account.passwordHash())) {
                throw failure();
            }
            account.changePassword(passwords.encode(password));
            audit.record("auth.password_changed", "user", account.id(), null);
            return account;
        }, false);
    }

    // ---------------------------------------------------------------- Accounts (clínica atual)

    @Override
    public InviteLink invite(String email, UUID roleId) {
        ClinicId clinic = CurrentClinic.require();
        String clean = normalize(email);
        return tx.execute(status -> {
            requireRole(roleId);
            if (accounts.existsByEmail(clean)) {
                throw new ConflictException("Este e-mail já tem uma conta.");
            }
            UserAccountEntity account = new UserAccountEntity(clinic.value(), clean, roleId, clock.instant());
            InviteLink link = issueInvite(account);
            accounts.save(account);
            audit.record("user.invited", "user", account.id(), null);
            return link;
        });
    }

    @Override
    public InviteLink newInvite(UUID userId) {
        return tx.execute(status -> {
            UserAccountEntity account = own(userId);
            if (account.status() == Status.DISABLED) {
                throw new InvalidStateTransitionException("Conta", Status.DISABLED, "novo convite");
            }
            InviteLink link = issueInvite(account);
            audit.record("user.invite_reissued", "user", userId, null);
            return link;
        });
    }

    @Override
    public void changeRole(UUID userId, UUID roleId) {
        tx.executeWithoutResult(status -> {
            RoleEntity role = requireRole(roleId);
            own(userId).changeRole(roleId);
            audit.record("user.role_changed", "user", userId, "novo perfil: " + role.name());
        });
    }

    @Override
    public void disable(UUID userId) {
        tx.executeWithoutResult(status -> {
            UserAccountEntity account = own(userId);
            if (account.status() == Status.DISABLED) {
                return;
            }
            account.disable(clock.instant());
            audit.record("user.disabled", "user", userId, null);
            // RN-S07: atendimento e solicitações devolvem ao setor o que estava com a pessoa, nesta transação.
            events.publishEvent(new AccountDisabled(userId, CurrentClinic.require()));
        });
    }

    @Override
    public void enable(UUID userId) {
        tx.executeWithoutResult(status -> {
            UserAccountEntity account = own(userId);
            if (account.status() != Status.DISABLED) {
                return;
            }
            account.enable();
            audit.record("user.enabled", "user", userId, null);
        });
    }

    @Override
    public Optional<View> find(UUID userId) {
        UUID clinic = CurrentClinic.require().value();
        return accounts.findByIdAndClinicId(userId, clinic).map(UserAccountEntity::view);
    }

    @Override
    public List<View> list() {
        UUID clinic = CurrentClinic.require().value();
        return accounts.findByClinicIdOrderByEmailAsc(clinic).stream().map(UserAccountEntity::view).toList();
    }

    @Override
    public long count() {
        return accounts.countByClinicId(CurrentClinic.require().value());
    }

    // ---------------------------------------------------------------- apoio

    private UserAccountEntity own(UUID userId) {
        return accounts.findByIdAndClinicId(userId, CurrentClinic.require().value())
                .orElseThrow(() -> new ResourceNotFoundException("Conta", userId));
    }

    private RoleEntity requireRole(UUID roleId) {
        return roles.findById(roleId).orElseThrow(() -> new ResourceNotFoundException("Perfil", roleId));
    }

    private InviteLink issueInvite(UserAccountEntity account) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expires = clock.instant().plus(INVITE_VALIDITY);
        account.setInvite(sha256(token), expires);
        return new InviteLink(account.id(), token, expires);
    }

    private StaffUser staffUser(UserAccountEntity account) {
        RoleEntity role = roles.findById(account.roleId()).orElseThrow();
        return new StaffUser(account.id(), new ClinicId(account.clinicId()), account.email(), role.id(), role.scope(),
                role.permissions());
    }

    /** Roda {@code work} numa transação aberta já com a clínica definida. Resultado nulo = login recusado. */
    private <T> T inClinic(ClinicId clinic, Supplier<T> work, boolean nullMeansFailure) {
        T result = CurrentClinic.call(clinic, () -> tx.execute(status -> work.get()));
        if (result == null && nullMeansFailure) {
            throw failure();
        }
        return result;
    }

    private static BadCredentialsException failure() {
        return new BadCredentialsException("E-mail ou senha inválidos");
    }

    static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    private static String validPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            throw new DomainValidationException("password",
                    "de " + MIN_PASSWORD + " a " + MAX_PASSWORD + " caracteres");
        }
        return password;
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
