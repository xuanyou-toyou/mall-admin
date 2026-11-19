package demo.mall.dao;

import demo.mall.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserDao 集成测试（会真的连数据库）。
 *
 * 为什么这是「集成测试」而不是「单元测试」？
 *   单元测试要隔离外部依赖（用 Mockito 把 DAO 换成假的）。
 *   这里要验证的恰恰是「DAO 和真实数据库配合得好不好」，
 *   所以必须连真库。这类测试叫集成测试。
 *
 * 测试数据会在前后都清理，不污染库。
 */
@SpringBootTest
@DisplayName("UserDao 连库验证")
class UserDaoTest {

    private static final String TEST_USERNAME = "__test_user__";
    private static final String TEST_PHONE = "__test_phone__";

    @Autowired
    private UserDao userDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM user WHERE username = ?", TEST_USERNAME);
    }

    @Test
    @DisplayName("插入 → 查回来，8 个字段全对")
    void insertAndFind() {
        System.out.println();
        System.out.println("========== 1. 准备一个用户 ==========");

        User user = new User();
        user.setUsername(TEST_USERNAME);
        // 真实业务里存的也是 BCrypt 哈希，测试走同一条路径
        user.setPassword(passwordEncoder.encode("123456"));
        user.setNickname("测试昵称");
        user.setPhone(TEST_PHONE);

        System.out.println("用户名         : " + user.getUsername());
        System.out.println("密码(加密后)   : " + user.getPassword());
        System.out.println("密码长度       : " + user.getPassword().length() + " 字符");

        System.out.println();
        System.out.println("========== 2. 插入数据库 ==========");
        System.out.println("插入前 user.getId() = " + user.getId() + "   （应该是 null）");
        boolean inserted = userDao.insert(user);
        System.out.println("insert 返回    : " + inserted);
        System.out.println("插入后 user.getId() = " + user.getId() + "   （应该拿到数据库生成的 id）");
        assertTrue(inserted, "插入应该成功");
        assertNotNull(user.getId(), "insert 必须把数据库生成的自增 id 回填到对象上");
        System.out.println("✅ KeyHolder 回填成功，id = " + user.getId());

        System.out.println();
        System.out.println("========== 3. 按用户名查回来 ==========");
        User found = userDao.findByUsername(TEST_USERNAME);
        assertNotNull(found, "应该能查到");

        System.out.println("id             : " + found.getId());
        System.out.println("username       : " + found.getUsername());
        System.out.println("password       : " + found.getPassword());
        System.out.println("nickname       : " + found.getNickname());
        System.out.println("phone          : " + found.getPhone());
        System.out.println("status         : " + found.getStatus());
        System.out.println("createdAt      : " + found.getCreatedAt());
        System.out.println("updatedAt      : " + found.getUpdatedAt());

        System.out.println();
        System.out.println("========== 4. 逐项断言 ==========");

        assertNotNull(found.getId());
        System.out.println("✅ id 由数据库自增生成：" + found.getId());

        assertEquals(TEST_USERNAME, found.getUsername());
        System.out.println("✅ username 一致");

        assertEquals(60, found.getPassword().length());
        System.out.println("✅ password 是 60 字符的 BCrypt 哈希");

        assertEquals("测试昵称", found.getNickname());
        System.out.println("✅ nickname 一致");

        assertEquals(TEST_PHONE, found.getPhone());
        System.out.println("✅ phone 一致");

        assertEquals(1, found.getStatus());
        System.out.println("✅ status 用了数据库默认值 1（插入时我们没传）");

        assertNotNull(found.getCreatedAt());
        assertNotNull(found.getUpdatedAt());
        System.out.println("✅ created_at / updated_at 由数据库 DEFAULT 自动填充");
        System.out.println("   >>> 所以 insert 的 SQL 里不需要写这两个字段");

        System.out.println();
        System.out.println("========== 5. 查不存在的用户 ==========");
        User notFound = userDao.findByUsername("__not_exist_user__");
        System.out.println("返回值         : " + notFound);
        assertNull(notFound);
        System.out.println("✅ 返回 null（不是 new User()），调用方用 == null 判断");

        System.out.println();
        System.out.println("========== 6. findAll 能看到它 ==========");
        List<User> all = userDao.findAll();
        System.out.println("用户总数       : " + all.size());
        assertTrue(all.stream().anyMatch(u -> TEST_USERNAME.equals(u.getUsername())));
        System.out.println("✅ findAll 里能找到");
        System.out.println();
    }
}
