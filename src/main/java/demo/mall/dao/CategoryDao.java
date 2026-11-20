package demo.mall.dao;

import demo.mall.model.Category;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品分类的数据访问层。
 *
 * ============================================================
 * DAO 层只做一件事：把 Java 对象和数据库表来回转换
 * ============================================================
 *
 *   Java 对象  →  数据库    insert / update / delete
 *   数据库    →  Java 对象  findById / findList
 *
 * 不在这里做业务校验（分类名是否重复、父分类是否存在），
 * 那是 Service 层的事。DAO 只负责「你让我存我就存」。
 *
 * ============================================================
 * 为什么用 @Repository
 * ============================================================
 *
 * 标记为 Spring 的「数据访问层组件」，应用启动时被扫描并注册成 Bean。
 * 它在语义上等价于 @Component，但含义更明确，而且
 * Spring 会额外把数据库抛出的 SQLException 转换成 DataAccessException（更友好的异常体系）。
 */
@Repository
public class CategoryDao {

    /**
     * JdbcTemplate 是 Spring 提供的数据库操作模板，封装了
     * 「拿连接 → 建语句 → 绑参数 → 执行 → 关连接 → 处理异常」这一整套流程。
     *
     * 声明成 final：依赖一旦注入就不允许再被替换，防止运行时被误改。
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造器注入。
     *
     * 为什么用构造器注入而不是字段上的 @Autowired？
     *   ① 依赖明确：看构造器就知道这个类需要什么
     *   ② 不可变：字段是 final，创建后不会被换掉
     *   ③ 易于测试：单元测试里可以手动 new CategoryDao(mockJdbcTemplate)
     */
    public CategoryDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 把 ResultSet 的一行数据转成 Category 对象。
     *
     * ============================================================
     * 三个关键点
     * ============================================================
     *
     * ① 参数含义
     *      rs      —— ResultSet，指向当前这一行
     *      rowNum  —— 当前行号（从 0 开始），绝大多数场景用不到
     *
     * ② 列名必须写「数据库列名」，不是 Java 字段名
     *      ✅ rs.getLong("parent_id")     数据库列名，下划线
     *      ❌ rs.getLong("parentId")      Java 字段名，驼峰，会抛 Column not found
     *
     * ③ 为什么用 getObject(列名, 类型) 而不是 getInt / getLong
     *      getInt("status")          列值为 NULL 时返回 0 —— 分不清「0」和「NULL」
     *      getObject("status", Integer.class)  列值为 NULL 时返回 null —— 能分清
     *
     *    时间类型更明显：
     *      rs.getTimestamp("created_at")  拿到的是 java.sql.Timestamp（旧 API）
     *      rs.getObject("created_at", LocalDateTime.class)  直接拿到 LocalDateTime（新 API）
     *
     * ============================================================
     * 为什么声明成 static final
     * ============================================================
     *
     * ① static —— 它不依赖任何实例状态，所有 CategoryDao 实例共用一份
     * ② final  —— 防止被意外覆盖
     * ③ 它是个函数式接口（只有一个方法），所以可以用 lambda 简写
     */
    private static final RowMapper<Category> CATEGORY_ROW_MAPPER = (rs, rowNum) -> {
        Category c = new Category();
        c.setId(rs.getLong("id"));
        c.setParentId(rs.getLong("parent_id"));
        c.setName(rs.getString("name"));
        c.setSort(rs.getObject("sort", Integer.class));
        c.setStatus(rs.getObject("status", Integer.class));
        c.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        c.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        return c;
    };

    /**
     * 插入一条分类，并把数据库生成的自增 id 回填到传入的对象上。
     *
     * ============================================================
     * 为什么不能用简单的 jdbcTemplate.update(sql, 参数...)
     * ============================================================
     *
     * 那个重载只返回「影响了几行」，拿不到自增主键。
     * 注册完分类后，业务上经常需要立刻知道新分类的 id（比如返回给前端），
     * 所以要用 KeyHolder 把主键捞回来。
     *
     * ============================================================
     * 为什么不用 SELECT LAST_INSERT_ID()
     * ============================================================
     *
     * 并发下会串：
     *   线程 A 插入 → 得到 id=5
     *   线程 B 插入 → 得到 id=6
     *   线程 A 执行 LAST_INSERT_ID() → 可能拿到 6（B 的）
     *
     * getGeneratedKeys() 是「连接级别」的，每个 PreparedStatement 拿自己的，不会串。
     *
     * ============================================================
     * 关于参数兜底
     * ============================================================
     *
     * sort 和 status 在表里都是 NOT NULL DEFAULT 0/1，但 DEFAULT 只在
     * 「INSERT 语句里完全不出现这个字段」时才生效。
     * 这里 SQL 显式列了这两个字段，如果传 null 就会报 Column cannot be null，
     * 所以在 Java 侧做兜底：
     *   sort   → null 时给 0
     *   status → null 时给 1（注意是 1 不是 0！0 表示停用，新建分类默认应该是正常）
     *
     * @param category 待插入的分类对象，插入成功后 id 会被回填
     * @return true 表示插入成功，false 表示失败（影响行数为 0）
     */
    public boolean insert(Category category) {
        String sql = "INSERT INTO category (parent_id, name, sort, status) VALUES (?,?,?,?)";

        // KeyHolder 用来接住数据库返回的自增主键
        KeyHolder keyHolder = new GeneratedKeyHolder();

        int rows = jdbcTemplate.update(con -> {
            // con 是 Spring 从连接池借出来的 Connection
            // Statement.RETURN_GENERATED_KEYS 告诉数据库「执行完把自增主键还给我」
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

            ps.setLong(1, category.getParentId());
            ps.setString(2, category.getName());

            // sort 为 null 时给 0（表里是 NOT NULL DEFAULT 0）
            if (category.getSort() != null) {
                ps.setInt(3, category.getSort());
            } else {
                ps.setInt(3, 0);
            }

            // status 为 null 时给 1（正常），不是 0（停用）
            if (category.getStatus() != null) {
                ps.setInt(4, category.getStatus());
            } else {
                ps.setInt(4, 1);
            }

            // ★ 这个 return 不能少：
            //   PreparedStatementCreator 接口要求返回 PreparedStatement，
            //   少了它编译报「找不到合适的方法」
            return ps;
        }, keyHolder);

        // 双重检查：真的插入了（rows > 0）而且真的拿到了主键（getKey() != null）
        if (rows > 0 && keyHolder.getKey() != null) {
            category.setId(keyHolder.getKey().longValue());
        }
        return rows > 0;
    }

    /**
     * 查询某个父分类下的所有子分类，按 sort 升序、id 兜底。
     *
     * ============================================================
     * 为什么返回 List 而不是单个 Category
     * ============================================================
     *
     * 一个父分类下可以有任意多个子分类（0 个、1 个、100 个都正常），
     * 天然是列表。即使实际只有一条，调用方也只能从 List 里取。
     *
     * 返回 List 还有个好处：查不到时返回空列表（不是 null），
     * 调用方直接 for 循环不会 NPE。
     *
     * ============================================================
     * 为什么 ORDER BY 要写两个字段
     * ============================================================
     *
     *   ORDER BY sort ASC, id ASC
     *                  ↑ 兜底
     *
     * 如果只写 ORDER BY sort，而两条记录 sort 值相同时，
     * MySQL 不保证返回顺序稳定 —— 分页时可能出现
     * 「同一条记录在第 1 页和第 2 页都出现」或者「某条记录哪页都没有」。
     *
     * 加上 id（唯一值）兜底，顺序就绝对稳定了。
     *
     * ============================================================
     * 三个参数版本 vs 两个参数版本
     * ============================================================
     *
     *   query(sql, rowMapper)                  → SQL 里不能有 ?
     *   query(sql, rowMapper, Object... args)  → 可以带 ?，按顺序填值
     *
     * 这里 SQL 有 ?，所以必须用三参数版本把 parentId 传进去。
     * 漏传的话，? 会原样发给 MySQL，报 SQL 语法错误。
     *
     * @param parentId 父分类 id，传 0L 表示查所有顶级分类
     * @return 子分类列表，查不到返回空列表（不是 null）
     */
    public List<Category> findByParentId(Long parentId) {
        String sql = "SELECT id, parent_id, name, sort, status, created_at, updated_at "
                + "FROM category WHERE parent_id = ? ORDER BY sort ASC, id ASC";
        return jdbcTemplate.query(sql, CATEGORY_ROW_MAPPER, parentId);
    }

    /**
     * 按主键查询单个分类。
     *
     * ============================================================
     * 为什么查不到返回 null，而不是抛异常
     * ============================================================
     *
     * 「查不到」是一种正常的业务情况，不是错误。
     *   比如前端传了个不存在的分类 id，Service 层判断 null 之后
     *   返回「分类不存在」的友好提示，这属于流程内。
     *
     * 如果 DAO 直接抛异常，Service 就得 try-catch，代码反而啰嗦。
     *
     * 但要注意区分：
     *   ✅ 返回 null    → 「确实没有这条记录」
     *   ❌ 返回空对象   → 调用方拿到一个所有字段都是 null 的对象，
     *                     以为「查到了但是字段为空」，最容易埋坑
     *
     * ============================================================
     * 为什么用 query 而不是 queryForObject
     * ============================================================
     *
     *   queryForObject  查不到会抛 EmptyResultDataAccessException，要额外 catch
     *   query           返回 List，自己判空，控制流更直观
     *
     * @param id 分类主键
     * @return 查到的分类，不存在时返回 null
     */
    public Category findById(Long id) {
        List<Category> list = jdbcTemplate.query(
                "SELECT id, parent_id, name, sort, status, created_at, updated_at "
                        + "FROM category WHERE id = ?",
                CATEGORY_ROW_MAPPER, id);
        // getFirst() 是 Java 21 的 SequencedCollection 方法，等价于 get(0)
        return list.isEmpty() ? null : list.getFirst();
    }
}
