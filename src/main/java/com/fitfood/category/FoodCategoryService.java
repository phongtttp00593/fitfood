package com.fitfood.category;

import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FoodCategoryService {
    private final FoodCategoryRepository repository;

    public FoodCategoryService(FoodCategoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<FoodCategory> search(String keyword, String status) {
        String key = keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT);
        String filter = status == null ? "all" : status;
        return repository.findAllByOrderByIdDesc().stream()
                .filter(c -> key.isEmpty()
                        || c.getName().toLowerCase(Locale.ROOT).contains(key)
                        || (c.getDescription() != null
                            && c.getDescription().toLowerCase(Locale.ROOT).contains(key)))
                .filter(c -> switch (filter) {
                    case "active" -> c.isActive();
                    case "inactive" -> !c.isActive();
                    default -> true;
                }).toList();
    }

    @Transactional(readOnly = true)
    public FoodCategory findById(Long id) {
        return repository.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy danh mục"));
    }

    @Transactional(readOnly = true)
    public boolean nameUsedByAnotherCategory(String name, Long currentId) {
        return repository.findByNameIgnoreCase(name).stream()
                .anyMatch(c -> currentId == null || !currentId.equals(c.getId()));
    }

    @Transactional
    public void save(CategoryForm form) {
        FoodCategory category = form.getId() == null
                ? new FoodCategory() : findById(form.getId());
        category.setName(form.getName().strip());
        category.setDescription(form.getDescription() == null
                ? null : form.getDescription().strip());
        category.setActive(form.isActive());
        repository.saveAndFlush(category);
    }

    @Transactional
    public void toggle(Long id) {
        FoodCategory category = findById(id);
        category.setActive(!category.isActive());
        repository.saveAndFlush(category);
    }

    @Transactional
    public void delete(Long id) {
        FoodCategory category = findById(id);
        repository.delete(category);
        repository.flush(); // Phát hiện lỗi khóa ngoại trước khi chuyển hướng.
    }

    @Transactional(readOnly = true)
    public long countAll() { return repository.count(); }
    @Transactional(readOnly = true)
    public long countActive() { return repository.countByActiveTrue(); }
    @Transactional(readOnly = true)
    public long countInactive() { return repository.countByActiveFalse(); }
}
