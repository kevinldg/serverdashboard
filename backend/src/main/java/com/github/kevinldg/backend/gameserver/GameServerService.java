package com.github.kevinldg.backend.gameserver;

import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.gameserver.GameServerStatus.Source;
import com.github.kevinldg.backend.gameserver.profile.ContainerTemplate;
import com.github.kevinldg.backend.gameserver.profile.GameServerProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Game server detection and manual classification.
 * <p>
 * Priority: manual classification, then the Docker label {@value #LABEL}, then the image name of a profile.
 * Label values: a profile ID (e.g. {@code minecraft-java}), {@code generic}/{@code true}/{@code yes} for a generic
 * game server, {@code none}/{@code false}/{@code no} for "not a game server". Unknown values count as generic.
 */
@Slf4j
@Service
public class GameServerService {

    public static final String LABEL = "serverdashboard.gameserver";
    static final String GENERIC_NAME = "Game server";

    private static final Set<String> NOT_GAME_SERVER_VALUES = Set.of("none", "false", "no");
    private static final Set<String> GENERIC_VALUES = Set.of("generic", "true", "yes", "");

    private final List<GameServerProfile> profiles;
    private final ContainerClassificationRepository repository;
    private final Clock clock;

    public GameServerService(List<GameServerProfile> profiles, ContainerClassificationRepository repository, Clock clock) {
        this.profiles = profiles.stream().sorted(Comparator.comparing(GameServerProfile::displayName)).toList();
        this.repository = repository;
        this.clock = clock;
    }

    /** The data detection is based on. */
    public record ContainerRef(String name, String image, Map<String, String> labels) {
    }

    /** A container template together with the profile it belongs to. */
    public record TemplateInfo(String profileId, String profileName, ContainerTemplate template) {
    }

    public List<TemplateInfo> listTemplates() {
        return profiles.stream()
                .flatMap(profile -> profile.templates().stream()
                        .map(template -> new TemplateInfo(profile.id(), profile.displayName(), template)))
                .toList();
    }

    public Optional<TemplateInfo> findTemplate(String templateId) {
        return listTemplates().stream().filter(info -> info.template().id().equals(templateId)).findFirst();
    }

    public List<GameServerProfileInfo> listProfiles() {
        return profiles.stream()
                .map(profile -> new GameServerProfileInfo(profile.id(), profile.displayName(), profile.imageNames()))
                .toList();
    }

    /**
     * Effective status of several containers, keyed by container name. Loads all manual classifications at once.
     */
    public Map<String, GameServerStatus> detectAll(List<ContainerRef> containers) {
        Map<String, ContainerClassification> classifications = StreamSupport
                .stream(repository.findAllById(containers.stream().map(ContainerRef::name).toList()).spliterator(), false)
                .collect(Collectors.toMap(ContainerClassification::getContainerName, Function.identity()));
        return containers.stream().collect(Collectors.toMap(ContainerRef::name,
                container -> detect(container, Optional.ofNullable(classifications.get(container.name()))),
                (first, second) -> first));
    }

    /** Effective status, including a manual classification. */
    public GameServerStatus detect(ContainerRef container) {
        return detect(container, repository.findById(container.name()));
    }

    /** What automatic detection (label and image) results in, ignoring a manual classification. */
    public GameServerStatus detectAutomatically(ContainerRef container) {
        String labelValue = container.labels() == null ? null : container.labels().get(LABEL);
        if (labelValue != null) {
            return fromLabel(labelValue);
        }
        return findProfileByImage(container.image())
                .map(profile -> new GameServerStatus(true, profile.id(), profile.displayName(), Source.IMAGE))
                .orElse(GameServerStatus.NOT_DETECTED);
    }

    public void classify(String containerName, ClassificationRequest request, AuthenticatedUser actor) {
        if (request.mode() == ClassificationRequest.Mode.AUTOMATIC) {
            repository.deleteById(containerName);
            log.info("User '{}' reset the classification of container '{}' to automatic", actor.getUsername(), containerName);
            return;
        }

        boolean gameServer = request.mode() == ClassificationRequest.Mode.GAME_SERVER;
        String profileId = gameServer ? request.profileId() : null;
        if (profileId != null && findProfile(profileId).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown game server profile: " + profileId);
        }

        ContainerClassification classification = new ContainerClassification();
        classification.setContainerName(containerName);
        classification.setGameServer(gameServer);
        classification.setProfileId(profileId);
        classification.setUpdatedAt(clock.instant());
        classification.setUpdatedBy(actor.getUsername());
        repository.save(classification);

        log.info("User '{}' classified container '{}' as {}", actor.getUsername(), containerName,
                !gameServer ? "not a game server" : profileId == null ? "generic game server" : profileId);
    }

    /** Called when a container is deleted through the application. */
    public void removeClassification(String containerName) {
        repository.deleteById(containerName);
    }

    /**
     * Reduces an image reference to its name: without registry, tag and digest, lower case,
     * e.g. {@code docker.io/itzg/minecraft-server:java25} becomes {@code itzg/minecraft-server}.
     */
    static String normalizeImage(String image) {
        if (image == null) {
            return "";
        }
        String name = image.trim().toLowerCase(Locale.ROOT);
        int digest = name.indexOf('@');
        if (digest >= 0) {
            name = name.substring(0, digest);
        }
        int tag = name.lastIndexOf(':');
        if (tag > name.lastIndexOf('/')) {
            name = name.substring(0, tag);
        }
        String[] parts = name.split("/", 2);
        if (parts.length == 2 && (parts[0].contains(".") || parts[0].contains(":") || parts[0].equals("localhost"))) {
            name = parts[1];
        }
        return name.startsWith("library/") ? name.substring("library/".length()) : name;
    }

    private GameServerStatus detect(ContainerRef container, Optional<ContainerClassification> classification) {
        return classification.map(this::fromClassification).orElseGet(() -> detectAutomatically(container));
    }

    private GameServerStatus fromClassification(ContainerClassification classification) {
        if (!classification.isGameServer()) {
            return new GameServerStatus(false, null, null, Source.MANUAL);
        }
        return withProfile(classification.getProfileId(), Source.MANUAL);
    }

    private GameServerStatus fromLabel(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (NOT_GAME_SERVER_VALUES.contains(normalized)) {
            return new GameServerStatus(false, null, null, Source.LABEL);
        }
        if (GENERIC_VALUES.contains(normalized)) {
            return withProfile(null, Source.LABEL);
        }
        return withProfile(normalized, Source.LABEL);
    }

    /** A game server with the given profile; unknown or missing profiles mean a generic game server. */
    private GameServerStatus withProfile(String profileId, Source source) {
        return Optional.ofNullable(profileId)
                .flatMap(this::findProfile)
                .map(profile -> new GameServerStatus(true, profile.id(), profile.displayName(), source))
                .orElse(new GameServerStatus(true, null, GENERIC_NAME, source));
    }

    private Optional<GameServerProfile> findProfile(String profileId) {
        return profiles.stream().filter(profile -> profile.id().equals(profileId)).findFirst();
    }

    private Optional<GameServerProfile> findProfileByImage(String image) {
        String name = normalizeImage(image);
        return profiles.stream().filter(profile -> profile.imageNames().contains(name)).findFirst();
    }
}
