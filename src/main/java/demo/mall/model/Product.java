package demo.mall.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体，对应数据库 product 表。
 *
 * ============================================================
 * 表结构（9 个字段）
 * ============================================================
 *
 *   表字段        实体字段       类型           说明
 *   ----------    ----------    -------------  ------------------------
 *   id            id            Long           主键，自增
 *   name          name          String         商品名
 *   sell          sell          BigDecimal     售价
 *   stock         stock         Integer        库存数量
 *   image_url     imageUrl      String         主图地址，可为 null
 *   category_id   categoryId    Long           所属分类 id
 *   status        status        Integer        0=下架 1=上架
 *   created_at    createdAt     LocalDateTime  创建时间
 *   updated_at    updatedAt     LocalDateTime  更新时间
 *
 * ============================================================
 * ★ 为什么 sell 用 BigDecimal，不用 double
 * ============================================================
 *
 * 因为 double 存不了精确的十进制小数。
 *
 *   double a = 0.1, b = 0.2;
 *   a + b 的结果是 0.30000000000000004，不是 0.3
 *
 * 累加 1000 次 0.1，结果是 99.9999999999986，不是 100。
 *
 * 商品价格要做乘法（数量 × 单价）、要累加（订单总额）、要比较（是否满减），
 * 任何一次精度丢失都会导致对账不一致、财务对不上账。
 *
 * 所以金额字段一律用 BigDecimal：
 *   ① 构造时用字符串：new BigDecimal("19.99")，不要 new BigDecimal(19.99)
 *   ② 运算用方法：add / subtract / multiply，不能用 + - ×
 *   ③ 比较用 compareTo()，不要用 equals()
 *      —— new BigDecimal("1.0").equals(new BigDecimal("1.00")) 是 false（精度不同）
 *      —— 但 compareTo 返回 0（数值相等）
 *
 * 数据库端对应 DECIMAL(10,2)：总共 10 位，小数 2 位。
 * 也就是最大 99999999.99。
 *
 * ============================================================
 * 关于 status 和「软删除」
 * ============================================================
 *
 * status = 0 表示下架，商品不删除，只是暂时不卖。
 *
 * 为什么不直接 DELETE？
 *   ① 历史订单里存着 product_id，删了商品订单详情就查不到
 *   ② 重新上架时 id 会变，和旧订单对不上
 *   ③ 数据报表统计历史销量需要完整数据
 *
 * 这就是「软删除」思想：不真删，只标记不可见。
 *
 * 前台商城查询加 WHERE status = 1，后台管理不加条件（看全部）。
 */
public class Product {

    /** 主键，数据库自增。插入前 null，插入后由 DAO 回填 */
    private Long id;

    /** 商品名 */
    private String name;

    /**
     * 售价，单位元，保留两位小数。
     *
     * 类型必须是 BigDecimal 而不是 double —— 原因见类注释。
     * 数据库对应 DECIMAL(10,2)，读取用 rs.getBigDecimal("sell")，
     * 写入用 ps.setBigDecimal(index, value)，两者天然匹配。
     */
    private BigDecimal sell;

    /** 库存数量。0 表示售罄（但商品仍可展示），下架要看 status */
    private Integer stock;

    /**
     * 主图地址，可为 null。
     *
     * 现在存的是相对路径（如 /img/iphone16.jpg），部署后可以换成 CDN 地址。
     * 列定义为 VARCHAR(255) DEFAULT NULL，所以空值是允许的。
     */
    private String imageUrl;

    /**
     * 所属分类 id，指向 category 表。
     *
     * 数据库列是 NOT NULL，所以这个值必须有。
     * 注意：这里只存 id，不存 Category 对象 ——
     * 表和表之间的关联在数据模型层用「外键 id」表示，
     * 如果要在业务层拿到完整分类信息，由 Service 层再查一次（或者联表查询）。
     */
    private Long categoryId;

    /**
     * 状态：0=下架，1=上架。
     *
     * 前台商城只查 status=1，后台管理可以查全部或按状态筛选。
     * DAO 里对 null 做了兜底（默认 1），因为列是 NOT NULL DEFAULT 1，
     * 显式传 NULL 会报错。
     */
    private Integer status;

    /** 创建时间，由数据库 DEFAULT CURRENT_TIMESTAMP 自动填 */
    private LocalDateTime createdAt;

    /** 更新时间，由数据库 ON UPDATE CURRENT_TIMESTAMP 自动维护 */
    private LocalDateTime updatedAt;

    /** 无参构造器。RowMapper 里要 new Product()，必须有 */
    public Product() {
    }

    /**
     * 全参构造器，方便测试构造对象。
     * 参数顺序和字段声明顺序一致 —— 传参时最容易出错的地方，注意别串位置。
     */
    public Product(Long id, String name, BigDecimal sell, Integer stock, String imageUrl,
                   Long categoryId, Integer status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.sell = sell;
        this.stock = stock;
        this.imageUrl = imageUrl;
        this.categoryId = categoryId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ==================== getter / setter ====================
    // RowMapper 靠 setter 填值，Jackson 靠 getter 输出 JSON，两边都不能少

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getSell() {
        return sell;
    }

    public void setSell(BigDecimal sell) {
        this.sell = sell;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
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
     * 拼成可读字符串，用于日志和调试。
     * 这个类没有敏感字段，全部输出。如果是 User 类就要略过 password。
     */
    @Override
    public String toString() {
        return "Product{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", sell=" + sell +
                ", stock=" + stock +
                ", imageUrl='" + imageUrl + '\'' +
                ", categoryId=" + categoryId +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
