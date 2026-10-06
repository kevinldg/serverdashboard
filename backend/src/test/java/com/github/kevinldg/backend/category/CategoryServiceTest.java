package com.github.kevinldg.backend.category;

import com.github.kevinldg.backend.audit.AuditAction;
import com.github.kevinldg.backend.audit.AuditEvent;
import com.github.kevinldg.backend.audit.AuditService;
import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.gameserver.ContainerClassificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");

    private final ContainerCategoryRepository categoryRepository = mock(ContainerCategoryRepository.class);
    private final ContainerClassificationRepository classificationRepository = mock(ContainerClassificationRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AuthenticatedUser admin = new AuthenticatedUser("a", "kevin", null, true, true, 0, Set.of());
    private final CategoryService service = new CategoryService(categoryRepository, classificationRepository, auditService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(categoryRepository.save(any(ContainerCategory.class))).thenAnswer(invocation -> {
            ContainerCategory category = invocation.getArgument(0);
            if (category.getId() == null) {
                category.setId("new-id");
            }
            return category;
        });
    }

    @Test
    void listsCategoriesByNameWithUsage() {
        when(categoryRepository.findAll()).thenReturn(List.of(category("2", "System", CategoryColor.GRAY),
                category("1", "communication", CategoryColor.BLUE)));
        when(classificationRepository.countByCategoryId("2")).thenReturn(3L);

        assertThat(service.listWithUsage()).containsExactly(
                new CategoryResponse("1", "communication", CategoryColor.BLUE, 0),
                new CategoryResponse("2", "System", CategoryColor.GRAY, 3));
        assertThat(service.list()).extracting(CategoryInfo::name).containsExactly("communication", "System");
    }

    @Test
    void createTrimsNameAndRecordsAuditEvent() {
        CategoryResponse response = service.create(new CategoryRequest("  System ", CategoryColor.GRAY), admin);

        assertThat(response).isEqualTo(new CategoryResponse("new-id", "System", CategoryColor.GRAY, 0));
        ArgumentCaptor<ContainerCategory> captor = ArgumentCaptor.forClass(ContainerCategory.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(NOW);

        AuditEvent event = recordedEvent();
        assertThat(event.action()).isEqualTo(AuditAction.CATEGORY_CREATE);
        assertThat(event.summary()).isEqualTo("Created category 'System'");
        assertThat(event.details()).isEqualTo(Map.of("color", "gray"));
    }

    @Test
    void duplicateNamesAreRejectedIgnoringCase() {
        when(categoryRepository.existsByNameIgnoreCase("system")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CategoryRequest("system", CategoryColor.RED), admin))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void concurrentDuplicateIsReportedAsConflict() {
        when(categoryRepository.save(any(ContainerCategory.class))).thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> service.create(new CategoryRequest("System", CategoryColor.RED), admin))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void updateRecordsChangedFields() {
        when(categoryRepository.findById("1")).thenReturn(Optional.of(category("1", "Comms", CategoryColor.BLUE)));

        service.update("1", new CategoryRequest("Communication", CategoryColor.TEAL), admin);

        AuditEvent event = recordedEvent();
        assertThat(event.action()).isEqualTo(AuditAction.CATEGORY_UPDATE);
        assertThat(event.details()).isEqualTo(Map.of("name", "Comms → Communication", "color", "blue → teal"));
    }

    @Test
    void changingOnlyTheCaseOfTheNameIsAllowed() {
        when(categoryRepository.findById("1")).thenReturn(Optional.of(category("1", "system", CategoryColor.GRAY)));
        when(categoryRepository.existsByNameIgnoreCase("System")).thenReturn(true);

        CategoryResponse response = service.update("1", new CategoryRequest("System", CategoryColor.GRAY), admin);

        assertThat(response.name()).isEqualTo("System");
        assertThat(recordedEvent().details()).isEqualTo(Map.of("name", "system → System"));
    }

    @Test
    void deleteRemovesAssignments() {
        when(categoryRepository.findById("1")).thenReturn(Optional.of(category("1", "System", CategoryColor.GRAY)));
        when(classificationRepository.deleteByCategoryId("1")).thenReturn(2L);

        service.delete("1", admin);

        verify(categoryRepository).delete(any(ContainerCategory.class));
        verify(classificationRepository).deleteByCategoryId("1");
        AuditEvent event = recordedEvent();
        assertThat(event.action()).isEqualTo(AuditAction.CATEGORY_DELETE);
        assertThat(event.summary()).isEqualTo("Deleted category 'System'");
        assertThat(event.details()).isEqualTo(Map.of("unassigned", "2 containers"));
    }

    @Test
    void unknownCategoryIsNotFound() {
        when(categoryRepository.findById("gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("gone", admin))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(classificationRepository, never()).deleteByCategoryId(any());
    }

    private AuditEvent recordedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        return captor.getValue();
    }

    private static ContainerCategory category(String id, String name, CategoryColor color) {
        ContainerCategory category = new ContainerCategory();
        category.setId(id);
        category.setName(name);
        category.setColor(color);
        return category;
    }
}
