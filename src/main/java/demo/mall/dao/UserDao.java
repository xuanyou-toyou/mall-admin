package demo.mall.dao;

import demo.mall.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class UserDao {

    private final JdbcTemplate jdbcTemplate;

    public UserDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** ResultSet 的一行 → User 对象 */
    private static final RowMapper<User> USER_ROW_MAPPER = (rs, rowNum) -> {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setPassword(rs.getString("password"));
        u.setNickname(rs.getString("nickname"));
        u.setPhone(rs.getString("phone"));
        u.setStatus(rs.getInt("status"));
        u.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        u.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));   // ← 补上
        return u;
    };

    public boolean insert(User user) {

        // SQL: INSERT INTO user (username, password, nickname, phone) VALUES (?, ?, ?, ?)
        // 用 jdbcTemplate.update(sql, 参数...)，返回影响行数
        // return 影响行数 > 0;
        String sql = "INSERT INTO user (username, password, nickname, phone) VALUES (?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int rows = jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getNickname());
            ps.setString(4, user.getPhone());
            return ps;
        }, keyHolder);
        if (rows > 0 && keyHolder.getKey() != null) {
            user.setId(keyHolder.getKey().longValue());
        }
        return rows > 0;
    }

    public List<User> findAll() {
        return jdbcTemplate.query("SELECT id, username, password, nickname, phone, status, created_at, updated_at FROM user ORDER BY id", USER_ROW_MAPPER);
    }

    /** 按用户名查用户，查不到返回 null */
    public User findByUsername(String username) {
        List<User> list = jdbcTemplate.query(
                "SELECT id, username, password, nickname, phone, status, created_at, updated_at FROM user WHERE username = ?",
                USER_ROW_MAPPER, username);
        return list.isEmpty() ? null : list.getFirst();
    }

    public User findByPhone(String phone) {
        List<User> list = jdbcTemplate.query(
                "SELECT id, username, password, nickname, phone, status, created_at, updated_at FROM user WHERE phone = ?",
                USER_ROW_MAPPER, phone);
        return list.isEmpty() ? null : list.getFirst();
    }
}