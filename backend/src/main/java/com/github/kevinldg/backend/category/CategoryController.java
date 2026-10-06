package com.github.kevinldg.backend.category;

import com.github.kevinldg.backend.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /** Available categories, for the classification dialog. */
    @GetMapping("/api/container-categories")
    @PreAuthorize("hasAnyAuthority('GAMESERVER_MANAGE', 'CATEGORY_MANAGE')")
    public List<CategoryInfo> list() {
        return categoryService.list();
    }

    @GetMapping("/api/admin/categories")
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public List<CategoryResponse> listWithUsage() {
        return categoryService.listWithUsage();
    }

    @PostMapping("/api/admin/categories")
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request,
                                                   @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request, actor));
    }

    @PutMapping("/api/admin/categories/{id}")
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public CategoryResponse update(@PathVariable String id, @Valid @RequestBody CategoryRequest request,
                                   @AuthenticationPrincipal AuthenticatedUser actor) {
        return categoryService.update(id, request, actor);
    }

    /** Containers with this category fall back to automatic detection. */
    @DeleteMapping("/api/admin/categories/{id}")
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable String id, @AuthenticationPrincipal AuthenticatedUser actor) {
        categoryService.delete(id, actor);
        return ResponseEntity.noContent().build();
    }
}
