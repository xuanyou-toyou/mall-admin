package demo.mall.dao;

import demo.mall.model.Category;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CategoryDao 集成测试（真连数据库）。
 */
@SpringBootTest
@DisplayName("CategoryDao 连库验证")
class CategoryDaoTest {

    @Autowired
    private CategoryDao categoryDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM category");
    }

    @Test
    @DisplayName("插入顶级分类 → 回填 id → 能查回来")
    void insertAndFindById() {
        System.out.println();
        System.out.println("========== 1. 插入顶级分类 ==========");

        Category c = new Category();
        c.setParentId(0L);
        c.setName("电子产品");
        c.setSort(1);
        // 故意不设 status，看会发生什么

        boolean ok = categoryDao.insert(c);
        System.out.println("insert 返回   : " + ok);
        System.out.println("回填的 id     : " + c.getId());
        assertTrue(ok);
        assertNotNull(c.getId(), "应该回填自增 id");

        System.out.println();
        System.out.println("========== 2. 按 id 查回来 ==========");
        Category found = categoryDao.findById(c.getId());
        assertNotNull(found);
        System.out.println("id        : " + found.getId());
        System.out.println("parentId  : " + found.getParentId());
        System.out.println("name      : " + found.getName());
        System.out.println("sort      : " + found.getSort());
        System.out.println("status    : " + found.getStatus() + "   ← 注意这个值");

        System.out.println();
        System.out.println(">>> 检查 status：我们插入时没设 status，期望是数据库默认值 1");
        assertEquals(1, found.getStatus(),
                "status 应该用数据库默认值 1（正常），不是 0（停用）");
        System.out.println("✅ status = 1");
    }

    @Test
    @DisplayName("查某父分类下的子分类")
    void findByParentIdTest() {
        System.out.println();
        System.out.println("========== 准备数据 ==========");

        Category parent = new Category();
        parent.setParentId(0L);
        parent.setName("电子产品");
        parent.setSort(1);
        parent.setStatus(1);
        categoryDao.insert(parent);
        System.out.println("父分类 id = " + parent.getId());

        for (String name : new String[]{"手机", "笔记本", "耳机"}) {
            Category child = new Category();
            child.setParentId(parent.getId());
            child.setName(name);
            child.setSort(1);
            child.setStatus(1);
            categoryDao.insert(child);
            System.out.println("  子分类: " + name);
        }

        System.out.println();
        System.out.println("========== 查子分类 ==========");
        List<Category> children = categoryDao.findByParentId(parent.getId());
        System.out.println("查到条数 = " + children.size());
        children.forEach(x -> System.out.println("  " + x.getId() + "  " + x.getName()));

        assertEquals(3, children.size(), "应该查到 3 个子分类");
    }
}
