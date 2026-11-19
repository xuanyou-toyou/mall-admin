package demo.mall.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

/**
 * 用户实体，对应数据库的 user 表。
 *
 * 字段和表的对应关系：
 *
 *   表字段        实体字段       类型
 *   ----------    ----------    -----------------
 *   id            id            Long
 *   username      username      String
 *   password      password      String   ← 存的是 BCrypt 加密后的，60 字符
 *   nickname      nickname      String   ← 可以为 null
 *   phone         phone         String
 *   status        status        Integer  ← 0=禁用 1=正常
 *   created_at    createdAt     LocalDateTime
 *   updated_at    updatedAt     LocalDateTime
 *
 * 为什么用 Integer 而不是 int 表示 status？
 *   因为 int 的默认值是 0，会被误解成「禁用」。
 *   Integer 默认值是 null，能区分「没设置」和「明确设为 0」。
 *
 * 为什么时间是 LocalDateTime 而不是 Date？
 *   Date 是 Java 早期的类，设计有缺陷，还不带时区信息。
 *   LocalDateTime 是 Java 8+ 的新 API，更清晰。
 *   JdbcTemplate 能自动把 MySQL 的 DATETIME 转成 LocalDateTime。
 *
 * ⚠️ 注意：这个类会被序列化成 JSON 返回给前端，
 *    所以 password 字段绝对要加 @JsonIgnore，否则密码哈希会泄露出去。
 *    （下面加了，别删）
 */
public class User {

    private Long id;
    private String username;

    /**
     * 存的是 BCrypt 加密后的字符串，固定 60 字符，不是明文。
     *
     * @JsonIgnore：万一这个实体被直接序列化成 JSON 返回（比如不小心把 User 对象
     * 当返回值），password 会被自动跳过，不泄露给前端。
     * 正常情况我们应该返回 DTO 而不是实体，这是双保险。
     */
    @JsonIgnore
    private String password;

    /** 昵称，可以为 null */
    private String nickname;

    private String phone;

    /** 状态：0=禁用，1=正常 */
    private Integer status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ==================== getter / setter ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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
     * toString 里刻意不输出 password。
     * 因为日志会打印对象，密码哈希不该出现在日志里。
     */
    @Override
    public String toString() {
        return "User{id=" + id
                + ", username='" + username + '\''
                + ", nickname='" + nickname + '\''
                + ", phone='" + phone + '\''
                + ", status=" + status
                + '}';
    }
}
