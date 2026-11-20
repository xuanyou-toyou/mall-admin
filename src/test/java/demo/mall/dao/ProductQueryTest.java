package demo.mall.dao;

import demo.mall.dto.ProductQuery;
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

/**
 * 分页 + 动态条件查询的集成测试。
 */
@SpringBootTest
@DisplayName("ProductDao 分页查询")
class ProductQueryTest {

    @Autowired private ProductDao productDao;
    @Autowired private CategoryDao categoryDao;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long catPhone;
    private Long catBook;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM category");

        Category c1 = new Category();
        c1.setParentId(0L); c1.setName("手机"); c1.setSort(1); c1.setStatus(1);
        categoryDao.insert(c1);
        catPhone = c1.getId();

        Category c2 = new Category();
        c2.setParentId(0L); c2.setName("笔记本"); c2.setSort(2); c2.setStatus(1);
        categoryDao.insert(c2);
        catBook = c2.getId();

        // 准备 5 个商品
        addProduct("iPhone 16", "5999.99", 10, catPhone, 1);
        addProduct("小米 15",   "3999.00", 20, catPhone, 1);
        addProduct("华为 Mate", "5499.50", 5,  catPhone, 1);
        addProduct("MacBook Pro", "12999.00", 3, catBook, 1);
        addProduct("下架商品",   "99.00",   0,  catBook, 0);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM category");
    }

    private void addProduct(String name, String sell, int stock, Long catId, int status) {
        Product p = new Product();
        p.setName(name);
        p.setSell(new BigDecimal(sell));
        p.setStock(stock);
        p.setCategoryId(catId);
        p.setStatus(status);
        productDao.insert(p);
    }

    @Test
    @DisplayName("① 无条件：查全部 5 条")
    void allProducts() {
        System.out.println();
        System.out.println("========== ① 无条件查全部 ==========");
        ProductQuery q = new ProductQuery();

        long total = productDao.count(q);
        List<Product> list = productDao.search(q);

        System.out.println("总数: " + total);
        System.out.println("本页条数: " + list.size());
        list.forEach(p -> System.out.println("  " + p.getName() + "  " + p.getSell()));

        assertEquals(5, total, "应该有 5 个商品");
        assertEquals(5, list.size());
    }

    @Test
    @DisplayName("② 按关键词搜")
    void byKeyword() {
        System.out.println();
        System.out.println("========== ② 关键词「iPhone」 ==========");
        ProductQuery q = new ProductQuery();
        q.setKeyword("iPhone");

        long total = productDao.count(q);
        List<Product> list = productDao.search(q);
        System.out.println("总数: " + total);
        list.forEach(p -> System.out.println("  " + p.getName()));

        assertEquals(1, total);
    }

    @Test
    @DisplayName("③ 按分类筛")
    void byCategory() {
        System.out.println();
        System.out.println("========== ③ 分类「手机」 ==========");
        ProductQuery q = new ProductQuery();
        q.setCategoryId(catPhone);

        long total = productDao.count(q);
        List<Product> list = productDao.search(q);
        System.out.println("总数: " + total);
        list.forEach(p -> System.out.println("  " + p.getName()));

        assertEquals(3, total);
    }

    @Test
    @DisplayName("④ 分页：每页 2 条，第 1 页")
    void page1() {
        System.out.println();
        System.out.println("========== ④ 分页 第1页 size=2 ==========");
        ProductQuery q = new ProductQuery();
        q.setPage(1);
        q.setSize(2);

        long total = productDao.count(q);
        List<Product> list = productDao.search(q);
        System.out.println("总条数: " + total + "（分页不该影响总数）");
        System.out.println("本页: " + list.size());
        list.forEach(p -> System.out.println("  " + p.getName()));

        assertEquals(5, total, "count 不该被 LIMIT 影响");
        assertEquals(2, list.size(), "第 1 页应该有 2 条");
    }

    @Test
    @DisplayName("⑤ 边界：价格 >= 5000 且 <= 6000")
    void byPriceRange() {
        System.out.println();
        System.out.println("========== ⑤ 价格 5000~6000 ==========");
        ProductQuery q = new ProductQuery();
        q.setMinPrice(new BigDecimal("5000"));
        q.setMaxPrice(new BigDecimal("6000"));

        List<Product> list = productDao.search(q);
        list.forEach(p -> System.out.println("  " + p.getName() + "  " + p.getSell()));

        // iPhone 5999.99 和 华为 5499.50 应该在范围内
        assertEquals(2, list.size(), "应该有 2 个商品在 5000-6000 区间");
    }
}
