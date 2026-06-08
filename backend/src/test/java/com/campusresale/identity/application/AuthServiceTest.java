// 文件功能：验证 AuthService 的登录策略、用户名规范化和注册校验。
package com.campusresale.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusresale.identity.api.AuthRequests.LoginRequest;
import com.campusresale.identity.api.AuthRequests.PasswordResetConfirmRequest;
import com.campusresale.identity.api.AuthRequests.PasswordResetRequest;
import com.campusresale.identity.api.AuthRequests.RegisterRequest;
import com.campusresale.identity.api.CurrentUserResponse;
import com.campusresale.identity.domain.PasswordResetTokenRecord;
import com.campusresale.identity.domain.UserAccount;
import com.campusresale.identity.infrastructure.PasswordResetTokenRepository;
import com.campusresale.identity.infrastructure.UserAccountRepository;
import com.campusresale.identity.infrastructure.UserSessionRepository;
import com.campusresale.files.FileService;
import com.campusresale.platform.api.ApiException;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.TokenHasher;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    private final UserAccountRepository userAccountRepository = org.mockito.Mockito.mock(UserAccountRepository.class);
    private final UserSessionRepository userSessionRepository = org.mockito.Mockito.mock(UserSessionRepository.class);
    private final PasswordResetTokenRepository passwordResetTokenRepository = org.mockito.Mockito.mock(PasswordResetTokenRepository.class);
    private final PasswordService passwordService = org.mockito.Mockito.mock(PasswordService.class);
    private final SessionTokenGenerator sessionTokenGenerator = org.mockito.Mockito.mock(SessionTokenGenerator.class);
    private final PasswordResetTokenGenerator passwordResetTokenGenerator = org.mockito.Mockito.mock(PasswordResetTokenGenerator.class);
    private final PasswordResetDeliveryService passwordResetDeliveryService = org.mockito.Mockito.mock(PasswordResetDeliveryService.class);
    private final FileService fileService = org.mockito.Mockito.mock(FileService.class);
    private final TokenHasher tokenHasher = org.mockito.Mockito.mock(TokenHasher.class);
    private final CurrentUserMapper currentUserMapper = org.mockito.Mockito.mock(CurrentUserMapper.class);

    private final AuthService authService = new AuthService(
            userAccountRepository,
            userSessionRepository,
            passwordResetTokenRepository,
            passwordService,
            sessionTokenGenerator,
            passwordResetTokenGenerator,
            passwordResetDeliveryService,
            fileService,
            tokenHasher,
            currentUserMapper
    );

    @Test
    void superAdminLoginRevokesOtherActiveSessions() {
        UserAccount superAdmin = userWithRoles("super_admin", "SUPER_ADMIN");
        stubSuccessfulLogin(superAdmin);

        authService.login(new LoginRequest("super_admin", "520zikejiang"), "127.0.0.1", "test");

        verify(userSessionRepository).revokeOtherActiveSessions(eq(1L), eq(99L), any());
    }

    @Test
    void contentAdminLoginDoesNotRevokeOtherSessions() {
        UserAccount contentAdmin = userWithRoles("content_admin", "CONTENT_ADMIN");
        stubSuccessfulLogin(contentAdmin);

        authService.login(new LoginRequest("content_admin", "520zikejiang"), "127.0.0.1", "test");

        verify(userSessionRepository, never()).revokeOtherActiveSessions(anyLong(), anyLong(), any());
    }

    @Test
    void normalizesUsernameBeforeLookup() {
        assertThat(authService.normalizeUsername("Alice_2026")).isEqualTo("alice_2026");
    }

    @Test
    void validatesNormalizedUsernameLengthOnRegister() {
        RegisterRequest request = new RegisterRequest("aa", "520zikejiang", "Alice", null);

        assertThatThrownBy(() -> authService.register(request, "127.0.0.1", "test"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("VALIDATION_FAILED"));
    }

    @Test
    void rejectsUsernameLongerThanTwentyCharacters() {
        RegisterRequest request = new RegisterRequest("abcdefghijklmnopqrstu", "520zikejiang", "Alice", null);

        assertThatThrownBy(() -> authService.register(request, "127.0.0.1", "test"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("VALIDATION_FAILED"));
    }

    @Test
    void rejectsUsernameWithSpaceOrSymbol() {
        RegisterRequest withSpace = new RegisterRequest("alice bob", "520zikejiang", "Alice", null);
        RegisterRequest withDash = new RegisterRequest("alice-bob", "520zikejiang", "Alice", null);

        assertThatThrownBy(() -> authService.register(withSpace, "127.0.0.1", "test"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("VALIDATION_FAILED"));
        assertThatThrownBy(() -> authService.register(withDash, "127.0.0.1", "test"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("VALIDATION_FAILED"));
    }

    @Test
    void acceptsUsernameWithUnderscore() {
        UserAccount userAccount = userWithRoles("alice_2026", "REGISTERED_USER");
        RegisterRequest request = new RegisterRequest("Alice_2026", "520zikejiang", "Alice", null);
        when(userAccountRepository.usernameExists("alice_2026")).thenReturn(false);
        when(passwordService.hash("520zikejiang")).thenReturn("hash");
        when(userAccountRepository.createRegisteredUser("alice_2026", "hash", "Alice", null)).thenReturn(userAccount);
        when(sessionTokenGenerator.generate()).thenReturn(new SessionToken("raw-token", "hash-token"));
        when(userSessionRepository.create(anyLong(), eq("hash-token"), any(), any(), any(), any(), any())).thenReturn(99L);
        when(currentUserMapper.fromUser(userAccount)).thenReturn(new CurrentUserResponse(
                1L,
                "alice_2026",
                "Alice",
                null,
                List.of("REGISTERED_USER"),
                "NONE",
                false
        ));

        AuthResult result = authService.register(request, "127.0.0.1", "test");

        assertThat(result.currentUser().username()).isEqualTo("alice_2026");
    }

    @Test
    void passwordResetRequestCreatesHashedTokenWhenEmailMatchesActiveAccount() {
        UserAccount userAccount = userWithRoles("alice", "REGISTERED_USER");
        Instant expiresAt = Instant.parse("2026-06-08T10:30:00Z");
        when(userAccountRepository.findActiveByPersonalEmail("alice@example.edu")).thenReturn(Optional.of(userAccount));
        when(passwordResetTokenGenerator.generate(any())).thenReturn(new PasswordResetToken("raw-token", "hash-token", expiresAt));

        var response = authService.requestPasswordReset(new PasswordResetRequest("Alice@Example.edu"));

        assertThat(response.accepted()).isTrue();
        verify(passwordResetTokenRepository).create(eq(1L), eq("hash-token"), eq("alice@example.edu"), eq(expiresAt), any());
        verify(passwordResetDeliveryService).deliver(userAccount, "alice@example.edu", "raw-token");
    }

    @Test
    void confirmPasswordResetConsumesTokenUpdatesPasswordAndRevokesSessions() {
        UserAccount userAccount = userWithRoles("alice", "REGISTERED_USER");
        PasswordResetTokenRecord tokenRecord = new PasswordResetTokenRecord(
                10L,
                userAccount.id(),
                "token-hash",
                "alice@example.edu",
                Instant.now().plusSeconds(60),
                null
        );
        when(tokenHasher.sha256("raw-token")).thenReturn("token-hash");
        when(passwordResetTokenRepository.findActiveByTokenHash(eq("token-hash"), any())).thenReturn(Optional.of(tokenRecord));
        when(userAccountRepository.findById(userAccount.id())).thenReturn(Optional.of(userAccount));
        when(passwordService.hash("new-password")).thenReturn("new-hash");

        var response = authService.confirmPasswordReset(new PasswordResetConfirmRequest("raw-token", "new-password"));

        assertThat(response.reset()).isTrue();
        verify(userAccountRepository).updatePasswordHash(eq(userAccount.id()), eq("new-hash"), any());
        verify(passwordResetTokenRepository).markConsumed(eq(10L), any());
        verify(userSessionRepository).revokeAllActiveSessions(eq(userAccount.id()), any());
    }

    @Test
    void deleteOwnAccountDisablesAccountAfterPasswordCheckAndRevokesSessions() {
        UserAccount userAccount = userWithRoles("alice", "REGISTERED_USER");
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(userAccount));
        when(passwordService.matches("520zikejiang", userAccount.passwordHash())).thenReturn(true);

        authService.deleteOwnAccount(principalWithRoles("REGISTERED_USER"), "520zikejiang");

        verify(userAccountRepository).updateAccountStatus(eq(1L), eq("DISABLED"), any());
        verify(userSessionRepository).revokeAllActiveSessions(eq(1L), any());
    }

    @Test
    void deleteOwnAccountRejectsSuperAdminSelfDeletion() {
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(userWithRoles("super_admin", "SUPER_ADMIN")));

        assertThatThrownBy(() -> authService.deleteOwnAccount(principalWithRoles("SUPER_ADMIN"), "520zikejiang"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("FORBIDDEN"));
    }

    private void stubSuccessfulLogin(UserAccount userAccount) {
        when(userAccountRepository.findByUsername(userAccount.username())).thenReturn(Optional.of(userAccount));
        when(passwordService.matches("520zikejiang", userAccount.passwordHash())).thenReturn(true);
        when(sessionTokenGenerator.generate()).thenReturn(new SessionToken("raw-token", "hash"));
        when(userSessionRepository.create(anyLong(), eq("hash"), any(), any(), any(), any(), any())).thenReturn(99L);
        when(currentUserMapper.fromUser(userAccount)).thenReturn(new CurrentUserResponse(
                userAccount.id(),
                userAccount.username(),
                userAccount.nickname(),
                null,
                List.copyOf(userAccount.roles()),
                "NONE",
                false
        ));
    }

    private UserAccount userWithRoles(String username, String... roles) {
        return new UserAccount(1L, username, "hash", "Test", null, "ACTIVE", Set.of(roles));
    }

    private CurrentPrincipal principalWithRoles(String... roles) {
        return new CurrentPrincipal(
                1L,
                "alice",
                "Alice",
                "ACTIVE",
                Set.of(roles),
                1L,
                Instant.now().plusSeconds(60),
                Instant.now().plusSeconds(120)
        );
    }
}
