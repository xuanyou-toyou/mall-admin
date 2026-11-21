package demo.mall.dao;

import demo.mall.model.Category;
import demo.mall.model.Product;
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
@DisplayName("CategoryDao 新增四个方法验证")
class CategoryDaoExtraTest {

    @Autowired private CategoryDao categoryDao;
    @Autowired private ProductDao productDao;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM category");
    }

    private Category newCat(Long parentId, String name, Integer sort, Integer status) {
        Category c = new Category();
        c.setParentId(parentId);
        c.setName(name);
        c.setSort(sort);
        c.setStatus(status);
        categoryDao.insert(c);
        return c;
    }

    @Test
    @DisplayName("① findAll：空表必须返回空列表，不是 null")
    void findAllEmpty() {
        System.out.println();
        System.out.println("========== findAll 空表 ==========");
        List<Category> all = categoryDao.findAll();
        System.out.println("  返回值: " + all);
        assertNotNull(all, "findAll 空表时不能返回 null");
        assertTrue(all.isEmpty(), "应该返回空列表");
        System.out.println("  ✅ 返回空列表，不是 null");

        // 验证可以直接遍历（调用方最关心的）
        int count = 0;
        for (Category c : all) { count++; }
        System.out.println("  ✅ 可以直接 for 循环，不 NPE");
    }

    @Test
    @DisplayName("② findAll：有数据时返回全部")
    void findAllHasData() {
        System.out.println();
        System.out.println("========== findAll 有数据 ==========");
        newCat(0L, "电子产品", 1, 1);
        newCat(0L, "服装", 2, 1);
        newCat(1L, "手机", 1, 1);

        List<Category> all = categoryDao.findAll();
        System.out.println("  查到 " + all.size() + " 条");
        all.forEach(c -> System.out.println("    " + c.getId() + " " + c.getName() + " parent=" + c.getParentId()));
        assertEquals(3, all.size());
    }

    @Test
    @DisplayName("③ update：正常修改")
    void updateNormal() {
        System.out.println();
        System.out.println("========== update 正常修改 ==========");
        Category c = newCat(0L, "旧名字", 1, 1);
        System.out.println("  改前: " + categoryDao.findById(c.getId()));

        c.setName("新名字");
        c.setSort(99);
        c.setStatus(0);
        boolean ok = categoryDao.update(c);
        System.out.println("  update 返回: " + ok);

        Category after = categoryDao.findById(c.getId());
        System.out.println("  改后: " + after);
        assertEquals("新名字", after.getName());
        assertEquals(99, after.getSort());
        assertEquals(0, after.getStatus());
        System.out.println("  ✅ 三个字段都改了");
    }

    @Test
    @DisplayName("④ update：parentId / sort / status 为 null 时不报错")
    void updateWithNulls() {
        System.out.println();
        System.out.println("========== update 传 null 字段 ==========");
        Category c = newCat(5L, "测试分类", 10, 1);

        Category partial = new Category();
        partial.setId(c.getId());
        partial.setName("只改了名字");
        // parentId / sort / status 都不设 → 全为 null

        System.out.println("  只传 id 和 name，其余为 null");
        boolean ok = categoryDao.update(partial);
        System.out.println("  update 返回: " + ok);

        Category after = categoryDao.findById(c.getId());
        System.out.println("  改后: " + after);
        System.out.println();
        System.out.println("  parentId 应该变成 0（null 兜底），现在: " + after.getParentId());
        System.out.println("  sort 应该变成 0（null 兜底），现在: " + after.getSort());
        System.out.println("  status 应该变成 1（null 兜底），现在: " + after.getStatus());

        assertEquals(0L, after.getParentId());
        assertEquals(0, after.getSort());
        assertEquals(1, after.getStatus());
        System.out.println("  ✅ 没有抛 NPE，null 都兜底了");
    }

    @Test
    @DisplayName("⑤ deleteById：删除成功 / 删不存在的")
    void deleteTest() {
        System.out.println();
        System.out.println("========== deleteById ==========");
        Category c = newCat(0L, "待删除", 1, 1);

        boolean ok = categoryDao.deleteById(c.getId());
        System.out.println("  删除存在的 id → " + ok + "（期望 true）");
        assertTrue(ok);
        assertNull(categoryDao.findById(c.getId()));
        System.out.println("  ✅ 确实删掉了");

        boolean ok2 = categoryDao.deleteById(99999L);
        System.out.println("  删除不存在的 id → " + ok2 + "（期望 false）");
        assertFalse(ok2);
        System.out.println("  ✅ 返回 false 而不是抛异常");
    }

    @Test
    @DisplayName("⑥ countByParentId / countByCategoryId")
    void countTest() {
        System.out.println();
        System.out.println("========== 两个 count 方法 ==========");
        Category root = newCat(0L, "电子产品", 1, 1);
        newCat(root.getId(), "手机", 1, 1);
        newCat(root.getId(), "笔记本", 2, 1);
        newCat(0L, "服装", 2, 1);

        System.out.println("  countByParentId(0)      = " + categoryDao.countByParentId(0L) + "（期望 2 个顶级）");
        System.out.println("  countByParentId(根id)   = " + categoryDao.countByParentId(root.getId()) + "（期望 2 个子分类）");
        System.out.println("  countByParentId(99999)  = " + categoryDao.countByParentId(99999L) + "（期望 0）");
        assertEquals(2, categoryDao.countByParentId(0L));
        assertEquals(2, categoryDao.countByParentId(root.getId()));
        assertEquals(0, categoryDao.countByParentId(99999L));

        Product p1 = new Product();
        p1.setName("iPhone"); p1.setSell(new BigDecimal("5999")); p1.setStock(10); p1.setCategoryId(root.getId());
        productDao.insert(p1);
        Product p2 = new Product();
        p2.setName("小米"); p2.setSell(new BigDecimal("3999")); p2.setStock(10); p2.setCategoryId(root.getId());
        productDao.insert(p2);

        System.out.println();
        System.out.println("  countByCategoryId(根id)  = " + productDao.countByCategoryId(root.getId()) + "（期望 2 个商品）");
        System.out.println("  countByCategoryId(99999) = " + productDao.countByCategoryId(99999L) + "（期望 0）");
        assertEquals(2, productDao.countByCategoryId(root.getId()));
        assertEquals(0, productDao.countByCategoryId(99999L));
        System.out.println("  ✅ 两个 count 都正确");
    }
}
