package demo.mall.dto;

/**
 * 登录成功的响应。
 *
 * 返回给前端的内容包含两部分的用途：
 *
 *   ① token 相关 —— 前端要拿去存起来，之后每个请求带上
 *   ② 用户基本信息 —— 前端可以直接拿去渲染「欢迎，张三」
 *      这样前端登录后不必再发一次「查个人信息」的请求
 *
 * 为什么这里不直接返回 User 实体？
 *   User 里有 password（虽然是哈希），还有 status、createdAt 等前端用不到的字段。
 *   返回一个「刚好够用」的 DTO，比返回一个「什么都给你」的实体更安全、更清晰。
 */
public class LoginResponse {

    /**
     * JWT token。
     *
     * 前端拿到后存起来（localStorage 或 Cookie），
     * 之后每个请求在请求头里带上：
     *   Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
     */
    private String token;

    /**
     * token 类型，固定 "Bearer"。
     *
     * 这是 OAuth 2.0 的约定。前端拼接请求头时会用到：
     *   "Authorization" = tokenType + " " + token
     */
    private String tokenType;

    /**
     * 有效期，单位毫秒。
     *
     * 前端可以据此做过期提醒，或者在快过期时自动续期。
     * 我们的配置是 7200000（2 小时）。
     */
    private Long expiresIn;

    /** 用户 id */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 昵称，可能为 null */
    private String nickname;

    // ==================== 构造器 ====================

    public LoginResponse() {
    }

    public LoginResponse(String token, String tokenType, Long expiresIn,
                         Long userId, String username, String nickname) {
        this.token = token;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
    }

    // ==================== getter / setter ====================

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * toString 里不输出 token。
     * token 等同于临时密码，不该出现在日志里。
     */
    @Override
    public String toString() {
        return "LoginResponse{userId=" + userId
                + ", username='" + username + '\''
                + ", expiresIn=" + expiresIn
                + '}';
    }
}
