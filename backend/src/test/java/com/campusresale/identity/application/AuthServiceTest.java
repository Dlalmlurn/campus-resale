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
import com.campusresale.identity.api.AuthRequests.RegisterRequest;
import com.campusresale.identity.api.CurrentUserResponse;
import com.campusresale.identity.domain.UserAccount;
import com.campusresale.identity.infrastructure.UserAccountRepository;
import com.campusresale.identity.infrastructure.UserSessionRepository;
import com.campusresale.platform.api.ApiException;
import com.campusresale.platform.security.TokenHasher;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    private final UserAccountRepository userAccountRepository = org.mockito.Mockito.mock(UserAccountRepository.class);
    private final UserSessionRepository userSessionRepository = org.mockito.Mockito.mock(UserSessionRepository.class);
    private final PasswordService passwordService = org.mockito.Mockito.mock(PasswordService.class);
    private final SessionTokenGenerator sessionTokenGenerator = org.mockito.Mockito.mock(SessionTokenGenerator.class);
    private final TokenHasher tokenHasher = org.mockito.Mockito.mock(TokenHasher.class);
    private final CurrentUserMapper currentUserMapper = org.mockito.Mockito.mock(CurrentUserMapper.class);

    private final AuthService authService = new AuthService(
            userAccountRepository,
            userSessionRepository,
            passwordService,
            sessionTokenGenerator,
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
                List.of("REGISTERED_USER"),
                "NONE",
                false
        ));

        AuthResult result = authService.register(request, "127.0.0.1", "test");

        assertThat(result.currentUser().username()).isEqualTo("alice_2026");
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
                List.copyOf(userAccount.roles()),
                "NONE",
                false
        ));
    }

    private UserAccount userWithRoles(String username, String... roles) {
        return new UserAccount(1L, username, "hash", "Test", "ACTIVE", Set.of(roles));
    }
}
