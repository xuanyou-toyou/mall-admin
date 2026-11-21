package demo.mall.controller;

import demo.mall.common.Result;
import demo.mall.dto.CategoryCreateRequest;
import demo.mall.dto.CategoryTreeNode;
import demo.mall.model.Category;
import demo.mall.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "分类", description = "商品分类管理")
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** ① 分类树 */
    @Operation(summary = "查询分类树", description = "返回带 children 的嵌套结构，最多支持三级")
    @GetMapping("/tree")
    public Result<List<CategoryTreeNode>> tree() {
        return Result.ok(categoryService.tree());
    }

    /** ② 新增 */
    @Operation(summary = "新增分类", description = "parentId 为 0 表示顶级分类")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody CategoryCreateRequest req) {
        Category category = new Category();
        category.setParentId(req.getParentId());
        category.setName(req.getName());
        category.setSort(req.getSort());
        category.setStatus(req.getStatus());
        categoryService.create(category);
        return Result.ok();
    }
    /** ③ 修改 */
    @Operation(summary = "修改分类")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Category category) {
        category.setId(id);
        categoryService.update(category);
        return Result.ok();
    }

    /** ④ 删除 */
    @Operation(summary = "删除分类", description = "有子分类或商品时不允许删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok();
    }
}