package demo.mall.dao;

import demo.mall.dto.ProductQuery;
import demo.mall.model.Product;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 商品的数据访问层。
 *
 * 当前实现了基础的单条读写（insert / findById），
 * 分页查询和动态条件筛选还没写。
 *
 * ============================================================
 * 和 CategoryDao 的区别
 * ============================================================
 *
 * ① sell 是 BigDecimal
 *      读取：rs.getBigDecimal("sell")
 *      写入：ps.setBigDecimal(2, product.getSell())
 *      不能用 getDouble —— 会丢精度
 *
 * ② imageUrl 可以为 null
 *      表里是 VARCHAR(255) DEFAULT NULL，
 *      ps.setString(4, null) 会写入 SQL NULL，这是允许的
 *
 * ③ categoryId 是 NOT NULL
 *      传 null 会在 ps.setLong(5, null) 时触发
 *      自动拆箱 NullPointerException，
 *      业务上应该由 Service 层保证一定有值
 */
@Repository
public class ProductDao {

    private final JdbcTemplate jdbcTemplate;

    public ProductDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 把 ResultSet 的一行转成 Product 对象（9 个字段）。
     *
     * 注意 sell 用的是 getBigDecimal，不是 getDouble —— 金额不能用浮点数，
     * 否则 5999.99 读出来会变成 5999.989999999999，再算就错。
     *
     * 注意列名是数据库的下划线命名（image_url、category_id），
     * 不是 Java 的驼峰命名（imageUrl、categoryId）。
     */
    private static final RowMapper<Product> PRODUCT_ROW_MAPPER = (rs, rowNum) -> {
        Product p = new Product();
        p.setId(rs.getLong("id"));
        p.setName(rs.getString("name"));
        p.setSell(rs.getBigDecimal("sell"));
        p.setStock(rs.getObject("stock", Integer.class));
        p.setImageUrl(rs.getString("image_url"));
        p.setCategoryId(rs.getLong("category_id"));
        p.setStatus(rs.getObject("status", Integer.class));
        p.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        p.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        return p;
    };

    /**
     * 插入一个商品，并把数据库生成的自增 id 回填到对象上。
     *
     * SQL 里只列了 6 个字段：
     *   name / sell / stock / image_url / category_id / status
     *
     * 另外三个不用传：
     *   id          —— 自增，数据库生成
     *   created_at  —— DEFAULT CURRENT_TIMESTAMP 自动填
     *   updated_at  —— DEFAULT CURRENT_TIMESTAMP 自动填
     *
     * 参数兜底：
     *   stock  → null 时给 0
     *   status → null 时给 1（上架），不是 0（下架）
     *
     * ⚠️ 潜在风险：
     *   sell 为 null 时，setBigDecimal 会写入 SQL NULL，
     *   而列定义是 NOT NULL，数据库会报错。
     *   categoryId 为 null 时，setLong 会自动拆箱 → NullPointerException。
     *   这两个校验应该由 Service 层保证，DAO 不重复做。
     *   后面加 Bean Validation 后，Controller 层就会拦掉。
     *
     * @param product 待插入的商品，成功后 id 被回填
     * @return true 表示插入成功
     */
    public boolean insert(Product product) {
        String sql = "INSERT INTO product (name, sell, stock, image_url, category_id, status) "
                + "VALUES (?,?,?,?,?,?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();

        int rows = jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

            ps.setString(1, product.getName());
            // ★ 金额必须用 setBigDecimal，不能用 setDouble
            ps.setBigDecimal(2, product.getSell());

            if (product.getStock() != null) {
                ps.setInt(3, product.getStock());
            } else {
                ps.setInt(3, 0);
            }

            // imageUrl 允许为 null，setString 传 null 会写入 SQL NULL
            ps.setString(4, product.getImageUrl());
            ps.setLong(5, product.getCategoryId());

            if (product.getStatus() != null) {
                ps.setInt(6, product.getStatus());
            } else {
                ps.setInt(6, 1);
            }

            return ps;
        }, keyHolder);

        if (rows > 0 && keyHolder.getKey() != null) {
            product.setId(keyHolder.getKey().longValue());
        }
        return rows > 0;
    }

    public boolean update(Product product) {
        String sql = "UPDATE product SET name = ?, sell = ?, stock = ?, image_url = ?, category_id = ?, status = ? WHERE id = ?";
        int rows = jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setString(1, product.getName());
            ps.setBigDecimal(2, product.getSell());
            if (product.getStock() != null) {
                ps.setInt(3, product.getStock());
            } else {
                ps.setInt(3, 0);
            }
            ps.setString(4, product.getImageUrl());
            ps.setLong(5, product.getCategoryId());
            if (product.getStatus() != null) {
                ps.setInt(6, product.getStatus());
            } else {
                ps.setInt(6, 1);
            }
            ps.setLong(7, product.getId());
            return ps;
        });
        return rows > 0;
    }

    /**
     * 按主键查询单个商品。
     *
     * ⚠️ 注意这条 SQL 查的是 product 表，字段也是 product 的 9 个字段。
     *    之前从 CategoryDao 复制过来时忘了改表名，
     *    导致查到的是 category 表的数据，RowMapper 找不到 sell 列。
     *    复制粘贴代码时，最容易漏改的就是「表名」和「字段列表」。
     *
     * @param id 商品主键
     * @return 查到的商品，不存在返回 null
     */
    public Product findById(Long id) {
        List<Product> list = jdbcTemplate.query(
                "SELECT id, name, sell, stock, image_url, category_id, status, created_at, updated_at "
                        + "FROM product WHERE id = ?",
                PRODUCT_ROW_MAPPER, id);
        return list.isEmpty() ? null : list.getFirst();
    }

    // ============================================================
    //  待实现
    // ============================================================
    //
    // ④ 分页查询 + 动态条件（关键词 / 分类 / 价格区间 / 状态）
    // ⑤ count 查询（WHERE 条件必须和 ④ 完全一致，否则总数对不上）
    //
    // 设计要点：
    //   - WHERE 条件用 StringBuilder 拼「结构」，参数用 List<Object> 收集「值」
    //   - SQL 里永远用 ? 占位，绝不把用户输入拼进 SQL 字符串（防注入）
    //   - 用 WHERE 1=1 起手，后面所有条件统一写 AND xxx
    //   - LIMIT ? OFFSET ? 的参数顺序：先 size 后 offset
    //   - offset = (page - 1) * size，别忘了减 1
    private String buildWhere(ProductQuery query, List<Object> params) {
        StringBuilder sql = new StringBuilder("WHERE 1=1");

        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            sql.append(" AND name LIKE ?");
            params.add("%" + query.getKeyword() + "%");
        }
        if (query.getCategoryId() != null) {
            sql.append(" AND category_id = ?");
            params.add(query.getCategoryId());
        }
        if (query.getMinPrice() != null && query.getMinPrice().compareTo(BigDecimal.ZERO) > 0) {
            sql.append(" AND sell >= ?");
            params.add(query.getMinPrice());
        }
        if (query.getMaxPrice() != null && query.getMaxPrice().compareTo(BigDecimal.ZERO) > 0) {
            sql.append(" AND sell <= ?");
            params.add(query.getMaxPrice());
        }
        if (query.getStatus() != null) {
            sql.append(" AND status = ?");
            params.add(query.getStatus());
        }

        return sql.toString();
    }

    public long count(ProductQuery query) {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(query, params);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product " + where, Long.class, params.toArray());
        return total == null ? 0L : total;
    }

    public List<Product> search(ProductQuery query) {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(query, params);
        int page = (query.getPage() == null || query.getPage() < 1) ? 1 : query.getPage();
        int limit = (query.getSize() == null || query.getSize() < 1) ? 10 : query.getSize();
        if (limit > 100) limit = 100;
        int offset = (page - 1) * limit;
        String sql = "SELECT id, name, sell, stock, image_url, category_id, status, created_at, updated_at FROM product " + where + " ORDER BY id DESC" + " LIMIT ? OFFSET ?";
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql, PRODUCT_ROW_MAPPER, params.toArray());
    }

    /** 统计某分类下的商品数量 */
    public int countByCategoryId(Long categoryId) {
        String sql = "SELECT count(*) FROM product WHERE category_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, categoryId);
        return count == null ? 0 : count;
    }
}
