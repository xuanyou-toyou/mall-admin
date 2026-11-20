package demo.mall.model;

import java.time.LocalDateTime;

/**
 * 商品分类实体，对应数据库 category 表。
 *
 * ============================================================
 * 表结构（7 个字段）
 * ============================================================
 *
 *   表字段        实体字段       类型           说明
 *   ----------    ----------    -------------  ------------------------
 *   id            id            Long           主键，自增
 *   parent_id     parentId      Long           父分类 id，0 表示顶级
 *   name          name          String         分类名
 *   sort          sort          Integer        排序值，小的排前面
 *   status        status        Integer        0=停用 1=正常
 *   created_at    createdAt     LocalDateTime  创建时间，数据库自动填
 *   updated_at    updatedAt     LocalDateTime  更新时间，数据库自动维护
 *
 * ============================================================
 * 关于 parentId —— 树形结构怎么用平表表示
 * ============================================================
 *
 * 数据库表只能一行一行地存，没有「树」这种结构。
 * 用 parent_id 记录「我爸爸是谁」，就能把树拆成平表：
 *
 *   id=1  parent_id=0  电子产品        ← 0 表示没有爸爸，是顶级
 *   id=2  parent_id=1  手机            ← 爸爸是 1，挂在电子产品下
 *   id=3  parent_id=1  笔记本          ← 爸爸是 1
 *   id=4  parent_id=0  服装            ← 另一个顶级
 *
 * 还原成树：
 *   电子产品
 *   ├── 手机
 *   └── 笔记本
 *   服装
 *
 * 这种设计叫「邻接表」，是电商分类最常用的方案，因为查询简单、插入快。
 *
 * ============================================================
 * 关于 status 和 sort 的分工
 * ============================================================
 *
 *    status  →  这个分类「能不能用」（停用后前台看不到）
 *    sort    →  这个分类「排第几」（运营手动调顺序）
 *
 * 两个字段解决的是完全不同的问题，别混。
 */
public class Category {

    /** 主键，数据库自增生成。插入前是 null，插入后由 DAO 回填 */
    private Long id;

    /**
     * 父分类 id。
     *
     * 0 表示顶级分类（没有父分类）。
     * 为什么用 0 而不是 NULL？
     *   用 0 的话查询统一写成 WHERE parent_id = 0，
     *   用 NULL 就得写 WHERE parent_id IS NULL，还要额外处理 null 判断，
     *   而且 0 永远不会和自增 id 冲突（自增从 1 开始）。
     */
    private Long parentId;

    /** 分类名。同一个父分类下不能重名（数据库有 uk_parent_name 联合唯一约束）*/
    private String name;

    /**
     * 排序值，数字小的排在前面。
     *
     * 查询时用 ORDER BY sort ASC, id ASC ——
     * sort 相同就按 id 兜底，保证顺序稳定，否则分页会出现重复或漏数据。
     *
     * 实际业务中建议用「稀疏排序」：100、200、300，
     * 中间插入新分类时用 150 就行，不用重排全部记录。
     */
    private Integer sort;

    /**
     * 状态：0=停用，1=正常。
     *
     * 停用后的分类，前台不显示入口，但分类下的商品还在（业务上要单独决定怎么处理）。
     * 这里的 DEFAULT 1 只在「INSERT 时不带这个字段」才生效，
     * 如果显式传 NULL 会报错（列是 NOT NULL），所以 DAO 里对 null 做了兜底。
     */
    private Integer status;

    /** 创建时间。由数据库 DEFAULT CURRENT_TIMESTAMP 自动填，插入时不用传 */
    private LocalDateTime createdAt;

    /** 更新时间。由数据库 ON UPDATE CURRENT_TIMESTAMP 自动维护，任何 UPDATE 都会刷新 */
    private LocalDateTime updatedAt;

    /** 无参构造器。JdbcTemplate 的 RowMapper 里要 new 对象，必须有它 */
    public Category() {
    }

    /**
     * 全参构造器，方便写测试和手工构造对象。
     * 注意参数顺序必须和字段声明顺序一致，否则容易传错（这是全参构造的通病）。
     */
    public Category(Long id, Long parentId, String name, Integer sort, Integer status,
                    LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.parentId = parentId;
        this.name = name;
        this.sort = sort;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ==================== getter / setter ====================
    // JdbcTemplate 的 RowMapper 用 setter 填值；
    // Jackson 序列化成 JSON 时用 getter 读值。两边都不能少。

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * 拼成可读字符串，主要用于打日志和调试。
     *
     * 为什么自定义而不是用 Lombok / IDE 生成的？
     *   这个类没有敏感字段，全部输出没问题。
     *   但如果是 User 类，绝对不能输出 password —— 那个类就刻意略过了密码字段。
     */
    @Override
    public String toString() {
        return "Category{" +
                "id=" + id +
                ", parentId=" + parentId +
                ", name='" + name + '\'' +
                ", sort=" + sort +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
