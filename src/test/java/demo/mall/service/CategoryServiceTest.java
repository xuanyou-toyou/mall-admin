package demo.mall.service;

import demo.mall.common.BizException;
import demo.mall.dao.CategoryDao;
import demo.mall.dto.CategoryTreeNode;
import demo.mall.model.Category;
import demo.mall.model.Product;
import demo.mall.dao.ProductDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("CategoryService 分类树与业务校验")
class CategoryServiceTest {

    @Autowired private CategoryService categoryService;
    @Autowired private CategoryDao categoryDao;
    @Autowired private ProductDao productDao;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM category");
    }

    @Test
    @DisplayName("① create：顶级分类（parentId=0）能建出来")
    void createRoot() {
        System.out.println();
        System.out.println("========== create 顶级分类 ==========");
        Category c = new Category();
        c.setParentId(0L);
        c.setName("电子产品");
        c.setSort(1);
        c.setStatus(1);

        try {
            categoryService.create(c);
            System.out.println("  ✅ 创建成功，id=" + c.getId());
        } catch (BizException e) {
            System.out.println("  ❌ 抛异常: " + e.getMessage());
            fail("顶级分类应该能创建");
        }
    }

    @Test
    @DisplayName("② create：父分类不存在 → 抛异常")
    void createWithBadParent() {
        System.out.println();
        System.out.println("========== create 父分类不存在 ==========");
        Category c = new Category();
        c.setParentId(99999L);
        c.setName("手机");
        c.setSort(1);
        c.setStatus(1);

        assertThrows(BizException.class, () -> categoryService.create(c));
        System.out.println("  ✅ 抛 BizException");
    }

    @Test
    @DisplayName("③ update：顶级分类只改 sort，能成功吗")
    void updateRootCategory() {
        System.out.println();
        System.out.println("========== update 顶级分类 ==========");

        Category root = new Category();
        root.setParentId(0L);
        root.setName("电子产品");
        root.setSort(1);
        root.setStatus(1);
        categoryService.create(root);
        System.out.println("  建了顶级分类 id=" + root.getId());

        // 只改 sort，parentId 还是 0
        Category upd = new Category();
        upd.setId(root.getId());
        upd.setParentId(0L);
        upd.setName("电子产品");
        upd.setSort(99);
        upd.setStatus(1);

        try {
            categoryService.update(upd);
            System.out.println("  ✅ 更新成功，新 sort=" + categoryDao.findById(root.getId()).getSort());
        } catch (BizException e) {
            System.out.println("  ❌ 抛异常: " + e.getMessage() + "  ← 顶级分类改不了");
        }
    }

    @Test
    @DisplayName("④ update：只改 sort 不改名字，能成功吗")
    void updateOnlySort() {
        System.out.println();
        System.out.println("========== update 只改 sort ==========");

        Category root = new Category();
        root.setParentId(0L);
        root.setName("电子产品");
        root.setSort(1);
        root.setStatus(1);
        categoryService.create(root);

        Category upd = new Category();
        upd.setId(root.getId());
        upd.setParentId(0L);
        upd.setName("电子产品");     // 名字没变
        upd.setSort(50);
        upd.setStatus(1);

        try {
            categoryService.update(upd);
            System.out.println("  ✅ 成功，新 sort=" + categoryDao.findById(root.getId()).getSort());
        } catch (BizException e) {
            System.out.println("  ❌ 抛异常: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("⑤ tree：三级分类树组装")
    void treeTest() {
        System.out.println();
        System.out.println("========== tree 三级分类 ==========");

        Category elec = mk(0L, "电子产品", 1);
        Category cloth = mk(0L, "服装", 2);
        Category phone = mk(elec.getId(), "手机", 1);
        Category book = mk(elec.getId(), "笔记本", 2);
        mk(phone.getId(), "苹果", 1);
        mk(phone.getId(), "安卓", 2);

        List<CategoryTreeNode> tree = categoryService.tree();
        System.out.println("  根节点数: " + tree.size());
        printTree(tree, 1);
        assertEquals(2, tree.size(), "应该有 2 个顶级分类");
    }

    private void printTree(List<CategoryTreeNode> nodes, int depth) {
        for (CategoryTreeNode n : nodes) {
            System.out.println("  " + "  ".repeat(depth) + n.getName() + " (id=" + n.getId() + ")");
            printTree(n.getChildren(), depth + 1);
        }
    }

    @Test
    @DisplayName("⑥ delete：三道保护")
    void deleteTest() {
        System.out.println();
        System.out.println("========== delete 三道保护 ==========");

        Category elec = mk(0L, "电子产品", 1);
        mk(elec.getId(), "手机", 1);

        // 有子分类
        try {
            categoryService.delete(elec.getId());
            System.out.println("  ❌ 有子分类却删成功了");
        } catch (BizException e) {
            System.out.println("  ✅ 有子分类 → " + e.getMessage());
        }

        // 不存在的 id
        assertThrows(BizException.class, () -> categoryService.delete(99999L));
        System.out.println("  ✅ 不存在的 id → 抛异常");

        // 以下有商品的分类
        Category empty = mk(0L, "空分类", 3);
        Product p = new Product();
        p.setName("test"); p.setSell(new BigDecimal("1")); p.setStock(1);
        p.setCategoryId(empty.getId());
        productDao.insert(p);

        try {
            categoryService.delete(empty.getId());
            System.out.println("  ❌ 有商品却删成功了");
        } catch (BizException e) {
            System.out.println("  ✅ 有商品 → " + e.getMessage());
        }
    }

    private Category mk(Long parentId, String name, int sort) {
        Category c = new Category();
        c.setParentId(parentId);
        c.setName(name);
        c.setSort(sort);
        c.setStatus(1);
        categoryService.create(c);
        return c;
    }
}
