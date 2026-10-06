package com.github.kevinldg.backend.category;

import com.github.kevinldg.backend.audit.AuditAction;
import com.github.kevinldg.backend.audit.AuditEvent;
import com.github.kevinldg.backend.audit.AuditService;
import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.gameserver.ContainerClassificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Management of container categories (e.g. "System", "Communication").
 * <p>
 * Categories are assigned to containers through the manual classification. Deleting a category removes these
 * assignments, so the affected containers fall back to automatic detection.
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    static final int MAX_NAME_LENGTH = 30;

    private final ContainerCategoryRepository categoryRepository;
    private final ContainerClassificationRepository classificationRepository;
    private final AuditService auditService;
    private final Clock clock;

    /** All categories, sorted by name. */
    public List<CategoryInfo> list() {
        return sortedCategories().stream().map(CategoryInfo::of).toList();
    }

    /** All categories with the number of containers they are assigned to, sorted by name. */
    public List<CategoryResponse> listWithUsage() {
        return sortedCategories().stream().map(this::toResponse).toList();
    }

    public CategoryResponse create(CategoryRequest request, AuthenticatedUser actor) {
        String name = request.name().trim();
        requireNameAvailable(name);

        Instant now = clock.instant();
        ContainerCategory category = new ContainerCategory();
        category.setName(name);
        category.setColor(request.color());
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        ContainerCategory saved = save(category);

        auditService.record(AuditEvent.success(actor.getUsername(), AuditAction.CATEGORY_CREATE, name,
                        "Created category '" + name + "'")
                .detail("color", format(request.color())));
        return toResponse(saved);
    }

    public CategoryResponse update(String id, CategoryRequest request, AuthenticatedUser actor) {
        ContainerCategory category = find(id);
        String name = request.name().trim();
        if (!name.equalsIgnoreCase(category.getName())) {
            requireNameAvailable(name);
        }

        String previousName = category.getName();
        CategoryColor previousColor = category.getColor();
        category.setName(name);
        category.setColor(request.color());
        category.setUpdatedAt(clock.instant());
        ContainerCategory saved = save(category);

        auditService.record(AuditEvent.success(actor.getUsername(), AuditAction.CATEGORY_UPDATE, name,
                        "Updated category '" + name + "'")
                .detail("name", previousName.equals(name) ? null : previousName + " → " + name)
                .detail("color", previousColor == request.color() ? null
                        : format(previousColor) + " → " + format(request.color())));
        return toResponse(saved);
    }

    public void delete(String id, AuthenticatedUser actor) {
        ContainerCategory category = find(id);
        categoryRepository.delete(category);
        long unassigned = classificationRepository.deleteByCategoryId(id);

        auditService.record(AuditEvent.success(actor.getUsername(), AuditAction.CATEGORY_DELETE, category.getName(),
                        "Deleted category '" + category.getName() + "'")
                .detail("unassigned", unassigned + (unassigned == 1 ? " container" : " containers")));
    }

    private List<ContainerCategory> sortedCategories() {
        return categoryRepository.findAll().stream()
                .sorted(Comparator.comparing(ContainerCategory::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void requireNameAvailable(String name) {
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "A category with this name already exists.");
        }
    }

    /** The unique index on the name also catches concurrent requests. */
    private ContainerCategory save(ContainerCategory category) {
        try {
            return categoryRepository.save(category);
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "A category with this name already exists.");
        }
    }

    private ContainerCategory find(String id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "The category does not exist (anymore)."));
    }

    private CategoryResponse toResponse(ContainerCategory category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getColor(),
                classificationRepository.countByCategoryId(category.getId()));
    }

    private static String format(CategoryColor color) {
        return color == null ? "none" : color.name().toLowerCase(Locale.ROOT);
    }
}
