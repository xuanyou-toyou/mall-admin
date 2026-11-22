package demo.mall.service;

import demo.mall.common.BizException;
import demo.mall.common.ResultCode;
import demo.mall.dao.CategoryDao;
import demo.mall.dao.UserDao;
import demo.mall.dto.RegisterRequest;
import demo.mall.model.Category;
import demo.mall.model.User;
import demo.mall.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 「写库失败」必须报 5xx 的回归测试（W13 的 3.5 与 3.6）。
 *
 * ============================================================
 * 为什么不用 @SpringBootTest + 真实数据库
 * ============================================================
 * 这两条要验证的是「DAO 返回 false 之后 Service 怎么反应」，
 * 而真实 MySQL 上几乎造不出这个场景：
 *   INSERT 要么成功（影响 1 行），要么直接抛异常（唯一键冲突、列非空等）。
 *   「SQL 执行了但影响 0 行」在单条 INSERT 里基本只出现在
 *   触发器/RULES/某些存储引擎/连接被中间件改写的场景 —— 测试环境里复现不了。
 *
 * 所以这里用「手写假 DAO」：继承真实 DAO，只把 insert 改成返回 false。
 * 好处是：
 *   ① 不连库、毫秒级、结果完全确定；
 *   ② 不依赖 Mockito（项目里没有单独声明它，用匿名子类更稳，
 *      也不会因为将来 JDK 禁止动态 agent 而失效）；
 *   ③ 生产代码里 INSERT 该有的样子一点没被篡改。
 *
 * 用匿名子类要注意：构造器仍然需要传 JdbcTemplate，这里传 null 是安全的 ——
 * 因为所有跟数据库打交道的可调用方法都被我们覆盖了，null 的那条路径永远不会执行。
 */
@DisplayName("写库失败 → 5xx（不是 400，也不是假装成功）")
class ServiceWriteFailureTest {

    /** JwtUtil 要求密钥至少 32 字节，这里给足；register 路径其实用不到它。 */
    private static final String JWT_SECRET = "mall-admin-unit-test-secret-key-0123456789abcdef";

    // ================================================================
    // 3.5 注册：userDao.insert() 返回 false
    // ================================================================
    @Test
    @DisplayName("3.5 注册写库失败 → BizException(500) 且文案不变")
    void registerInsertFailureIsServerError() {
        System.out.println();
        System.out.println("========== 3.5 注册写库失败 ==========");

        // 假 DAO：唯一性校验全部「通过」（查不到重复），但 insert 返回 false
        UserDao failingDao = new UserDao(null) {
            @Override
            public User findByUsername(String username) {
                return null;   // 用户名没被占用 → 通过校验
            }

            @Override
            public User findByPhone(String phone) {
                return null;   // 手机号没被占用 → 通过校验
            }

            @Override
            public boolean insert(User user) {
                return false;  // 模拟「SQL 执行了但影响行数为 0」
            }
        };

        UserService service = new UserService(
                failingDao, new BCryptPasswordEncoder(), new JwtUtil(JWT_SECRET, 7_200_000L));

        RegisterRequest req = new RegisterRequest();
        req.setUsername("ut_writefail");
        req.setPassword("abc123456");
        req.setPhone("13900000003");

        BizException e = assertThrows(BizException.class, () -> service.register(req));
        System.out.println("  异常 code    : " + e.getCode());
        System.out.println("  异常 message : " + e.getMessage());

        // 旧代码这里是 400（单参构造固定 400）—— 这条断言会让它红
        assertEquals(ResultCode.INTERNAL_ERROR.getCode(), e.getCode(),
                "写库失败属服务器故障，code 必须是 500，实际 " + e.getCode());
        assertEquals("注册失败，请稍后重试", e.getMessage(),
                "对外文案必须保持原文案，不能泄漏表名/SQL");
        System.out.println("  ✅ 500 且文案未变");
    }

    // ================================================================
    // 3.6 分类：categoryDao.insert() 返回值被丢弃
    // ================================================================
    @Test
    @DisplayName("3.6 分类 insert 返回 false → BizException(500)，且未用 null 的 id 打日志")
    void categoryInsertFailureIsServerError() {
        System.out.println();
        System.out.println("========== 3.6 分类写库失败 ==========");

        CategoryDao failingDao = new CategoryDao(null) {
            @Override
            public boolean insert(Category category) {
                return false;   // 模拟「SQL 执行了但影响行数为 0」
            }
        };

        // productDao 传 null：create() 路径根本不碰它
        CategoryService service = new CategoryService(failingDao, null);

        Category category = new Category();
        category.setParentId(0L);       // 顶级分类 → 走不到 findById(parentId)
        category.setName("测试分类");
        // 故意不设 sort / status，让 Service 去补默认值（顺便覆盖默认值逻辑）

        BizException e = assertThrows(BizException.class, () -> service.create(category));
        System.out.println("  异常 code    : " + e.getCode());
        System.out.println("  异常 message : " + e.getMessage());

        // 旧代码不检查返回值 → 这里根本不会抛异常，assertThrows 直接失败
        assertEquals(ResultCode.INTERNAL_ERROR.getCode(), e.getCode(),
                "分类写库失败属服务器故障，code 必须是 500，实际 " + e.getCode());
        assertEquals("分类创建失败，请稍后重试", e.getMessage());

        // insert 失败时 DAO 不会回填主键 —— 这也正是「日志里不能打 id」的原因：
        // 打出来只会是 "id=null"。这里锁定这个事实，防止以后有人把它当成功路径用。
        assertNull(category.getId(), "insert 失败时主键不应被回填，日志里不能依赖它");
        System.out.println("  ✅ 500、文案正确、id 仍为 null");
    }
}
