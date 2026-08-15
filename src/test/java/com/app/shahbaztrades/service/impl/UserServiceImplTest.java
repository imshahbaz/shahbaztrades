package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.auth.GoogleUser;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.model.enums.UserTheme;
import com.app.shahbaztrades.repo.UserRepo;
import com.app.shahbaztrades.repo.redis.AuthDataRedisRepo;
import com.app.shahbaztrades.service.UserService;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private UserRepo userRepo;
    @Mock
    private SequenceGeneratorService sequenceGeneratorService;
    @Mock
    private AuthDataRedisRepo<UserDto> authDataRedisRepo;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(mongoTemplate, userRepo, sequenceGeneratorService, authDataRedisRepo);
    }

    private UpdateResult modified(long count) {
        return UpdateResult.acknowledged(1, count, null);
    }

    // --- lookup -----------------------------------------------------------

    @Test
    void findByUserIdOrEmailOrMobile_returnsNullWhenNoIdentifierIsSupplied() {
        // Without criteria the query would match an arbitrary user, so it must short-circuit.
        assertNull(service.findByUserIdOrEmailOrMobile(0L, "", 0L));
        assertNull(service.findByUserIdOrEmailOrMobile(null, null, null));
        verify(mongoTemplate, never()).findOne(any(Query.class), eq(User.class));
    }

    @Test
    void findByUserIdOrEmailOrMobile_orsEverySuppliedIdentifier() {
        User user = User.builder().userId(7L).build();
        when(mongoTemplate.findOne(any(Query.class), eq(User.class))).thenReturn(user);

        assertSame(user, service.findByUserIdOrEmailOrMobile(7L, "a@b.com", 99L));

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findOne(query.capture(), eq(User.class));
        String json = query.getValue().getQueryObject().toJson();
        assertTrue(json.contains("$or"));
        assertTrue(json.contains("a@b.com"));
    }

    @Test
    void findByUserIdOrEmailOrMobile_ignoresNonPositiveIdsAndBlankEmail() {
        when(mongoTemplate.findOne(any(Query.class), eq(User.class))).thenReturn(null);

        service.findByUserIdOrEmailOrMobile(0L, "a@b.com", 0L);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findOne(query.capture(), eq(User.class));
        String json = query.getValue().getQueryObject().toJson();
        assertTrue(json.contains("a@b.com"));
        assertTrue(!json.contains("mobile"), "a zero mobile must not become a search criterion");
    }

    // --- creation ---------------------------------------------------------

    @Test
    void createUser_assignsTheNextSequenceId() {
        when(mongoTemplate.findOne(any(Query.class), eq(User.class))).thenReturn(null);
        when(sequenceGeneratorService.getNextSequence(UserService.USER_ID_SEQ)).thenReturn(101L);
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User created = service.createUser(UserDto.builder().email("a@b.com").password("pw").build());

        assertEquals(101L, created.getUserId());
    }

    @Test
    void createUser_generatesAPasswordWhenNoneWasSupplied() {
        // Google sign-ups have no password; the account must still get an unguessable hash.
        when(mongoTemplate.findOne(any(Query.class), eq(User.class))).thenReturn(null);
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User created = service.createUser(UserDto.builder().email("a@b.com").build());

        assertNotNull(created.getPassword());
    }

    @Test
    void createUser_rejectsADuplicateEmail() {
        when(mongoTemplate.findOne(any(Query.class), eq(User.class)))
                .thenReturn(User.builder().userId(1L).build());

        assertThrows(ResourceAlreadyExistsException.class,
                () -> service.createUser(UserDto.builder().email("a@b.com").build()));
        verify(userRepo, never()).save(any(User.class));
    }

    // --- google -----------------------------------------------------------

    @Test
    void findOrCreateGoogleUser_createsWhenTheEmailIsUnknown() {
        when(mongoTemplate.findOne(any(Query.class), eq(User.class))).thenReturn(null);
        when(sequenceGeneratorService.getNextSequence(UserService.USER_ID_SEQ)).thenReturn(5L);
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = service.findOrCreateGoogleUser(GoogleUser.builder()
                .email("jane@example.com").givenName("Jane").familyName("Doe").name("Jane Doe").picture("p1")
                .build());

        assertEquals(5L, user.getUserId());
        assertEquals("jane@example.com", user.getEmail());
    }

    @Test
    void findOrCreateGoogleUser_syncsAChangedPictureAndName() {
        User existing = User.builder().userId(5L).email("jane@example.com").name("Jane D").profile("old").build();
        when(mongoTemplate.findOne(any(Query.class), eq(User.class))).thenReturn(existing);

        User user = service.findOrCreateGoogleUser(GoogleUser.builder()
                .email("jane@example.com").name("Jane Doe").picture("new").build());

        assertEquals("new", user.getProfile());
        assertEquals("Jane Doe", user.getName());
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(User.class));
    }

    @Test
    void findOrCreateGoogleUser_skipsTheWriteWhenNothingChanged() {
        User existing = User.builder().userId(5L).email("j@x.com").name("Jane").profile("p").build();
        when(mongoTemplate.findOne(any(Query.class), eq(User.class))).thenReturn(existing);

        service.findOrCreateGoogleUser(GoogleUser.builder().email("j@x.com").name("Jane").picture("p").build());

        // An unconditional updateFirst on every login would be a write per request.
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(User.class));
    }

    @Test
    void findOrCreateGoogleUser_doesNotBlankAnExistingNameWhenGoogleSendsNone() {
        User existing = User.builder().userId(5L).email("j@x.com").name("Jane").profile("p").build();
        when(mongoTemplate.findOne(any(Query.class), eq(User.class))).thenReturn(existing);

        User user = service.findOrCreateGoogleUser(
                GoogleUser.builder().email("j@x.com").name("").picture("p").build());

        assertEquals("Jane", user.getName());
    }

    // --- profile updates --------------------------------------------------

    @Test
    void updateUserName_persistsAndInvalidatesTheAuthCache() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(User.class)))
                .thenReturn(modified(1));

        service.updateUserName(UserDto.builder().userId(5L).username("newname").build());

        // A stale /me response would keep showing the old username otherwise.
        verify(authDataRedisRepo).delete("5");
    }

    @Test
    void updateUserName_rejectsAnInvalidRequest() {
        assertThrows(BadRequestException.class,
                () -> service.updateUserName(UserDto.builder().userId(0L).username("x").build()));
        assertThrows(BadRequestException.class,
                () -> service.updateUserName(UserDto.builder().userId(5L).username("").build()));
    }

    @Test
    void updateUserName_throwsWhenNoDocumentMatched() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(User.class)))
                .thenReturn(modified(0));

        assertThrows(NotFoundException.class,
                () -> service.updateUserName(UserDto.builder().userId(5L).username("x").build()));
        verify(authDataRedisRepo, never()).delete(anyString());
    }

    @Test
    void updateUserTheme_returnsTheAppliedThemeAndClearsTheCache() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(User.class)))
                .thenReturn(modified(1));

        UserTheme theme = service.updateUserTheme(
                UserDto.builder().userId(5L).theme(UserTheme.LIGHT).build());

        assertEquals(UserTheme.LIGHT, theme);
        verify(authDataRedisRepo).delete("5");
    }

    @Test
    void updateUserTheme_rejectsAMissingTheme() {
        assertThrows(BadRequestException.class,
                () -> service.updateUserTheme(UserDto.builder().userId(5L).build()));
    }

    @Test
    void findByIds_delegatesToTheRepository() {
        when(userRepo.findAllById(Set.of(1L, 2L))).thenReturn(List.of(User.builder().userId(1L).build()));

        assertEquals(1, service.findByIds(Set.of(1L, 2L)).size());
    }
}
