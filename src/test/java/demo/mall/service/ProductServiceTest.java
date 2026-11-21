package demo.mall.service;

import demo.mall.common.BizException;
import demo.mall.dao.CategoryDao;
import demo.mall.dao.ProductDao;
import demo.mall.dto.PageResult;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("ProductService 业务校验与分页")
class ProductServiceTest {

    @Autowired private ProductService productService;
    @Autowired private ProductDao productDao;
    @Autowired private CategoryDao categoryDao;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long catId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM category");
        Category c = new Category();
        c.setParentId(0L); c.setName("测试分类"); c.setSort(1); c.setStatus(1);
        categoryDao.insert(c);
        catId = c.getId();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM category");
    }

    private Product sample(String name, String sell, int stock) {
        Product p = new Product();
        p.setName(name);
        p.setSell(new BigDecimal(sell));
        p.setStock(stock);
        p.setCategoryId(catId);
        return p;
    }

    @Test
    @DisplayName("① create：四条业务校验")
    void createValidation() {
        System.out.println();
        System.out.println("========== 业务校验 ==========");

        // 分类不存在
        Product bad1 = sample("商品A", "100", 10);
        bad1.setCategoryId(99999L);
        assertThrows(BizException.class, () -> productService.create(bad1));
        System.out.println("  ✅ 分类不存在 → 抛异常");

        // 价格为 0
        assertThrows(BizException.class, () -> productService.create(sample("商品B", "0", 10)));
        System.out.println("  ✅ 价格 0 → 抛异常");

        // 价格为负
        assertThrows(BizException.class, () -> productService.create(sample("商品C", "-1", 10)));
        System.out.println("  ✅ 价格为负 → 抛异常");

        // 库存为负
        assertThrows(BizException.class, () -> productService.create(sample("商品D", "100", -1)));
        System.out.println("  ✅ 库存为负 → 抛异常");

        // 名称为空
        assertThrows(BizException.class, () -> productService.create(sample("   ", "100", 10)));
        System.out.println("  ✅ 名称为空 → 抛异常");

        // 正常
        Product ok = sample("正常商品", "99.99", 10);
        productService.create(ok);
        assertNotNull(ok.getId());
        System.out.println("  ✅ 正常商品创建成功 id=" + ok.getId());
    }

    @Test
    @DisplayName("② updateStatus：改了之后数据库里的值变了吗")
    void updateStatusTest() {
        System.out.println();
        System.out.println("========== updateStatus 是否生效 ==========");

        Product p = sample("待下架商品", "100", 10);
        productService.create(p);
        Long id = p.getId();
        System.out.println("  创建商品 id=" + id + "，初始 status=1（默认）");

        Product before = productDao.findById(id);
        System.out.println("  改之前数据库里的 status = " + before.getStatus());

        productService.updateStatus(id, 0);
        System.out.println("  调用 updateStatus(id, 0)");

        Product after = productDao.findById(id);
        System.out.println("  改之后数据库里的 status = " + after.getStatus());

        assertEquals(0, after.getStatus(), "updateStatus 应该真的把数据库里的 status 改成 0");
        System.out.println("  ✅ 生效");
    }

    @Test
    @DisplayName("③ updateStatus：id 不存在")
    void updateStatusNotFound() {
        System.out.println();
        System.out.println("========== updateStatus 传不存在的 id ==========");
        try {
            productService.updateStatus(99999L, 0);
            System.out.println("  ❌ 没有抛异常（应该抛「商品不存在」）");
        } catch (NullPointerException e) {
            System.out.println("  ❌ NullPointerException（没做 null 判断）");
        } catch (BizException e) {
            System.out.println("  ✅ 抛 BizException: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("④ page()：page/size 为 null 会怎样")
    void pageWithNullParams() {
        System.out.println();
        System.out.println("========== page() 传 null ==========");

        productService.create(sample("商品1", "100", 1));

        ProductQuery q = new ProductQuery();
        q.setPage(null);
        q.setSize(null);
        System.out.println("  query.page = null, query.size = null");

        try {
            PageResult<Product> result = productService.page(q);
            System.out.println("  返回：total=" + result.getTotal()
                    + " page=" + result.getPage() + " size=" + result.getSize());
            System.out.println("  ⚠️ 没抛异常，但 page/size 的值是否合理？");
        } catch (NullPointerException e) {
            System.out.println("  ❌ NullPointerException: 自动拆箱失败");
        } catch (Exception e) {
            System.out.println("  ❌ " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Test
    @DisplayName("⑤ page()：size 传超大值，PageResult 里的 size 是多少")
    void pageWithHugeSize() {
        System.out.println();
        System.out.println("========== page() size=9999 ==========");

        for (int i = 1; i <= 5; i++) {
            productService.create(sample("商品" + i, "100", 1));
        }

        ProductQuery q = new ProductQuery();
        q.setPage(1);
        q.setSize(9999);

        PageResult<Product> result = productService.page(q);
        System.out.println("  实际返回条数 : " + result.getList().size());
        System.out.println("  PageResult.size : " + result.getSize() + "   ← Dao 内部兜底成 100");
        System.out.println("  PageResult.totalPages : " + result.getTotalPages());

        System.out.println();
        if (result.getSize() == 9999) {
            System.out.println("  ❌ PageResult 里存的是原始的 9999，和 Dao 实际用的 100 不一致");
        } else {
            System.out.println("  ✅ PageResult 存的是兜底后的值");
        }
    }
}
