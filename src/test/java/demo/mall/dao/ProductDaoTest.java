package demo.mall.dao;

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
@DisplayName("ProductDao 连库验证")
class ProductDaoTest {

    @Autowired private ProductDao productDao;
    @Autowired private CategoryDao categoryDao;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM category");
    }

    private Product sample() {
        Product p = new Product();
        p.setName("iPhone 16");
        p.setSell(new BigDecimal("5999.99"));
        p.setStock(100);
        p.setImageUrl("/img/iphone16.jpg");
        p.setCategoryId(1L);
        return p;
    }

    @Test
    @DisplayName("insert → findById，金额必须精确")
    void insertAndFind() {
        System.out.println();
        System.out.println("========== 1. 插入商品 ==========");
        Product p = sample();
        System.out.println("售价: " + p.getSell() + "  （BigDecimal 精确值）");

        boolean ok = productDao.insert(p);
        System.out.println("insert 返回 : " + ok);
        System.out.println("回填 id     : " + p.getId());
        assertTrue(ok);
        assertNotNull(p.getId());

        System.out.println();
        System.out.println("========== 2. 按 id 查回来 ==========");
        Product found = productDao.findById(p.getId());
        assertNotNull(found, "应该能查到");

        System.out.println("name       : " + found.getName());
        System.out.println("sell       : " + found.getSell());
        System.out.println("stock      : " + found.getStock());
        System.out.println("imageUrl   : " + found.getImageUrl());
        System.out.println("categoryId : " + found.getCategoryId());
        System.out.println("status     : " + found.getStatus());

        System.out.println();
        System.out.println("========== 3. 金额精度检查 ==========");
        assertEquals(0, new BigDecimal("5999.99").compareTo(found.getSell()),
                "金额读回来必须精确等于 5999.99");
        System.out.println("✅ 金额精确：5999.99（如果用了 double 这里会变成 5999.989999...）");

        assertEquals(1, found.getStatus(), "status 默认应该是 1");
        System.out.println("✅ status 默认值 1");
    }
}
