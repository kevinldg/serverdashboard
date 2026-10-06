package com.github.kevinldg.backend.gameserver;

import com.github.kevinldg.backend.audit.AuditService;
import com.github.kevinldg.backend.audit.AuditEvent;
import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.category.CategoryColor;
import com.github.kevinldg.backend.category.CategoryInfo;
import com.github.kevinldg.backend.category.ContainerCategory;
import com.github.kevinldg.backend.category.ContainerCategoryRepository;
import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.gameserver.ClassificationRequest.Mode;
import com.github.kevinldg.backend.gameserver.GameServerService.ContainerRef;
import com.github.kevinldg.backend.gameserver.GameServerStatus.Source;
import com.github.kevinldg.backend.gameserver.profile.MinecraftBedrockProfile;
import com.github.kevinldg.backend.gameserver.profile.MinecraftJavaProfile;
import com.github.kevinldg.backend.gameserver.profile.SatisfactoryProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameServerServiceTest {

    private final AuditService auditService = mock(AuditService.class);

    // Containers as found on the production server (labels shortened)
    private static final ContainerRef MINECRAFT = new ContainerRef("minecraft-server01", "itzg/minecraft-server:java25",
            Map.of("org.opencontainers.image.title", "docker-minecraft-server"));
    private static final ContainerRef TEAMSPEAK = new ContainerRef("teamspeak", "teamspeak",
            Map.of("com.teamspeak.title", "TeamSpeak 3 Server"));
    private static final ContainerRef PORTAINER = new ContainerRef("portainer", "portainer/portainer-ce:2.21.4", Map.of());

    private final ContainerClassificationRepository repository = mock(ContainerClassificationRepository.class);
    private final ContainerCategoryRepository categoryRepository = mock(ContainerCategoryRepository.class);
    private final AuthenticatedUser admin = new AuthenticatedUser("a", "kevin", null, true, true, 0, Set.of());
    private GameServerService service;

    @BeforeEach
    void setUp() {
        service = new GameServerService(
                List.of(new SatisfactoryProfile(), new MinecraftJavaProfile(), new MinecraftBedrockProfile()),
                repository, categoryRepository, auditService, Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC));
        when(repository.findById(any())).thenReturn(Optional.empty());
        when(repository.findAllById(anyIterable())).thenReturn(List.of());
    }

    @Test
    void productionContainersAreDetectedByImage() {
        assertThat(service.detect(MINECRAFT))
                .isEqualTo(new GameServerStatus(true, "minecraft-java", "Minecraft (Java Edition)", Source.IMAGE));
        assertThat(service.detect(TEAMSPEAK)).isEqualTo(GameServerStatus.NOT_DETECTED);
        assertThat(service.detect(PORTAINER)).isEqualTo(GameServerStatus.NOT_DETECTED);
    }

    @Test
    void allProfilesAreDetectedByTheirImages() {
        assertThat(service.detect(new ContainerRef("b", "itzg/minecraft-bedrock-server:latest", Map.of())).profileId())
                .isEqualTo("minecraft-bedrock");
        assertThat(service.detect(new ContainerRef("s", "wolveix/satisfactory-server:v1.9", Map.of())).profileId())
                .isEqualTo("satisfactory");
    }

    @Test
    void imageNamesAreNormalized() {
        assertThat(GameServerService.normalizeImage("itzg/minecraft-server")).isEqualTo("itzg/minecraft-server");
        assertThat(GameServerService.normalizeImage("ITZG/Minecraft-Server:java25")).isEqualTo("itzg/minecraft-server");
        assertThat(GameServerService.normalizeImage("docker.io/itzg/minecraft-server:latest")).isEqualTo("itzg/minecraft-server");
        assertThat(GameServerService.normalizeImage("itzg/minecraft-server@sha256:abc")).isEqualTo("itzg/minecraft-server");
        assertThat(GameServerService.normalizeImage("localhost:5000/itzg/minecraft-server:1")).isEqualTo("itzg/minecraft-server");
        assertThat(GameServerService.normalizeImage("docker.io/library/nginx:1.27")).isEqualTo("nginx");
        assertThat(GameServerService.normalizeImage(null)).isEmpty();
    }

    @Test
    void similarImagesAreNotDetected() {
        assertThat(service.detect(new ContainerRef("x", "someone/minecraft-server", Map.of())).gameServer()).isFalse();
        assertThat(service.detect(new ContainerRef("x", "itzg/minecraft-server-proxy", Map.of())).gameServer()).isFalse();
    }

    @Test
    void labelOverridesImage() {
        assertThat(service.detect(withLabel(TEAMSPEAK, "generic")))
                .isEqualTo(new GameServerStatus(true, null, "Game server", Source.LABEL));
        assertThat(service.detect(withLabel(TEAMSPEAK, "satisfactory")).profileId()).isEqualTo("satisfactory");
        assertThat(service.detect(withLabel(MINECRAFT, "none")))
                .isEqualTo(new GameServerStatus(false, null, null, Source.LABEL));
    }

    @Test
    void labelValuesAreLenient() {
        assertThat(service.detect(withLabel(PORTAINER, " TRUE ")).gameServer()).isTrue();
        assertThat(service.detect(withLabel(PORTAINER, "yes")).gameServer()).isTrue();
        assertThat(service.detect(withLabel(PORTAINER, "")).gameServer()).isTrue();
        assertThat(service.detect(withLabel(MINECRAFT, "false")).gameServer()).isFalse();
        assertThat(service.detect(withLabel(MINECRAFT, "No")).gameServer()).isFalse();
        // Unknown profile: still clearly meant as a game server
        assertThat(service.detect(withLabel(PORTAINER, "valheim")))
                .isEqualTo(new GameServerStatus(true, null, "Game server", Source.LABEL));
    }

    @Test
    void manualClassificationOverridesEverything() {
        when(repository.findById("minecraft-server01")).thenReturn(Optional.of(classification("minecraft-server01", false, null)));
        when(repository.findById("teamspeak")).thenReturn(Optional.of(classification("teamspeak", true, null)));

        assertThat(service.detect(withLabel(MINECRAFT, "minecraft-java")))
                .isEqualTo(new GameServerStatus(false, null, null, Source.MANUAL));
        assertThat(service.detect(TEAMSPEAK)).isEqualTo(new GameServerStatus(true, null, "Game server", Source.MANUAL));
        // The automatic result is still available, e.g. to show what "automatic" would mean
        assertThat(service.detectAutomatically(MINECRAFT).source()).isEqualTo(Source.IMAGE);
    }

    @Test
    void detectAllLoadsClassificationsOnce() {
        when(repository.findAllById(anyIterable())).thenReturn(List.of(classification("teamspeak", true, "satisfactory")));

        Map<String, GameServerStatus> result = service.detectAll(List.of(MINECRAFT, TEAMSPEAK, PORTAINER));

        assertThat(result.get("minecraft-server01").profileId()).isEqualTo("minecraft-java");
        assertThat(result.get("teamspeak")).isEqualTo(new GameServerStatus(true, "satisfactory", "Satisfactory", Source.MANUAL));
        assertThat(result.get("portainer").gameServer()).isFalse();
        verify(repository, never()).findById(any());
    }

    @Test
    void classifyStoresManualClassification() {
        service.classify("teamspeak", new ClassificationRequest(Mode.GAME_SERVER, null, null), admin);

        ArgumentCaptor<ContainerClassification> captor = ArgumentCaptor.forClass(ContainerClassification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getContainerName()).isEqualTo("teamspeak");
        assertThat(captor.getValue().isGameServer()).isTrue();
        assertThat(captor.getValue().getProfileId()).isNull();
        assertThat(captor.getValue().getUpdatedBy()).isEqualTo("kevin");
    }

    @Test
    void notGameServerIgnoresProfile() {
        service.classify("x", new ClassificationRequest(Mode.NOT_GAME_SERVER, "minecraft-java", null), admin);

        ArgumentCaptor<ContainerClassification> captor = ArgumentCaptor.forClass(ContainerClassification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isGameServer()).isFalse();
        assertThat(captor.getValue().getProfileId()).isNull();
    }

    @Test
    void automaticRemovesManualClassification() {
        service.classify("teamspeak", new ClassificationRequest(Mode.AUTOMATIC, null, null), admin);

        verify(repository).deleteById("teamspeak");
        verify(repository, never()).save(any());
    }

    @Test
    void unknownProfileIsRejected() {
        assertThatThrownBy(() -> service.classify("x", new ClassificationRequest(Mode.GAME_SERVER, "valheim", null), admin))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(repository, never()).save(any());
    }

    @Test
    void manualCategoryOverridesAutomaticDetection() {
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(category("cat-1", "Communication")));
        when(repository.findById("minecraft-server01")).thenReturn(Optional.of(withCategory("minecraft-server01", "cat-1")));

        assertThat(service.detect(MINECRAFT)).isEqualTo(new GameServerStatus(false, null, null, Source.MANUAL,
                new CategoryInfo("cat-1", "Communication", CategoryColor.BLUE)));
    }

    @Test
    void classificationWithDeletedCategoryFallsBackToAutomaticDetection() {
        when(categoryRepository.findById("gone")).thenReturn(Optional.empty());
        when(repository.findById("minecraft-server01")).thenReturn(Optional.of(withCategory("minecraft-server01", "gone")));

        assertThat(service.detect(MINECRAFT).source()).isEqualTo(Source.IMAGE);
    }

    @Test
    void detectAllLoadsCategoriesOnce() {
        when(repository.findAllById(anyIterable())).thenReturn(List.of(
                withCategory("teamspeak", "cat-1"), withCategory("portainer", "cat-2"), withCategory("minecraft-server01", "gone")));
        when(categoryRepository.findAllById(anyIterable()))
                .thenReturn(List.of(category("cat-1", "Communication"), category("cat-2", "System")));

        Map<String, GameServerStatus> result = service.detectAll(List.of(MINECRAFT, TEAMSPEAK, PORTAINER));

        assertThat(result.get("teamspeak").category().name()).isEqualTo("Communication");
        assertThat(result.get("portainer").category().name()).isEqualTo("System");
        assertThat(result.get("minecraft-server01").source()).isEqualTo(Source.IMAGE);
        verify(categoryRepository).findAllById(anyIterable());
        verify(categoryRepository, never()).findById(any());
    }

    @Test
    void detectAllSkipsCategoryLookupWithoutCategories() {
        service.detectAll(List.of(MINECRAFT, TEAMSPEAK));

        verify(categoryRepository, never()).findAllById(anyIterable());
    }

    @Test
    void classifyStoresCategory() {
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(category("cat-1", "Communication")));

        service.classify("teamspeak", new ClassificationRequest(Mode.CATEGORY, "minecraft-java", "cat-1"), admin);

        ArgumentCaptor<ContainerClassification> captor = ArgumentCaptor.forClass(ContainerClassification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isGameServer()).isFalse();
        assertThat(captor.getValue().getProfileId()).isNull();
        assertThat(captor.getValue().getCategoryId()).isEqualTo("cat-1");
        verify(auditService).record(argThat((AuditEvent event) ->
                event.summary().equals("Classified container 'teamspeak' as category 'Communication'")));
    }

    @Test
    void unknownOrMissingCategoryIsRejected() {
        when(categoryRepository.findById("gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.classify("x", new ClassificationRequest(Mode.CATEGORY, null, "gone"), admin))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.classify("x", new ClassificationRequest(Mode.CATEGORY, null, null), admin))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(repository, never()).save(any());
    }

    @Test
    void gameServerClassificationIgnoresCategory() {
        service.classify("x", new ClassificationRequest(Mode.GAME_SERVER, null, "cat-1"), admin);

        ArgumentCaptor<ContainerClassification> captor = ArgumentCaptor.forClass(ContainerClassification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCategoryId()).isNull();
        verify(categoryRepository, never()).findById(any());
    }

    @Test
    void profilesAreListedByName() {
        assertThat(service.listProfiles()).extracting(GameServerProfileInfo::name)
                .containsExactly("Minecraft (Bedrock Edition)", "Minecraft (Java Edition)", "Satisfactory");
    }

    private static ContainerRef withLabel(ContainerRef container, String value) {
        return new ContainerRef(container.name(), container.image(), Map.of(GameServerService.LABEL, value));
    }

    private static ContainerClassification withCategory(String name, String categoryId) {
        ContainerClassification classification = classification(name, false, null);
        classification.setCategoryId(categoryId);
        return classification;
    }

    private static ContainerCategory category(String id, String name) {
        ContainerCategory category = new ContainerCategory();
        category.setId(id);
        category.setName(name);
        category.setColor(CategoryColor.BLUE);
        return category;
    }

    private static ContainerClassification classification(String name, boolean gameServer, String profileId) {
        ContainerClassification classification = new ContainerClassification();
        classification.setContainerName(name);
        classification.setGameServer(gameServer);
        classification.setProfileId(profileId);
        return classification;
    }
}
