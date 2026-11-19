package demo.mall.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求。
 *
 * 登录和注册的校验强度不一样：
 *
 *   注册时要校验格式（用户名长度、密码强度、手机号格式），
 *   因为要把这些数据存进数据库。
 *
 *   登录时只需要「非空」就够了，不需要校验格式。
 *   因为登录只是拿输入去比对，格式对不对由比对结果自然体现 ——
 *   用户输了个 3 位用户名，查不到就返回「用户名或密码错误」，没必要单独提示格式。
 *
 * 这也是一个安全上的考虑：
 *   如果登录接口报「用户名长度必须是 4-20 位」，
 *   攻击者就知道了这个系统用户名的最小长度，是信息泄露。
 */
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    // ==================== getter / setter ====================

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

    /** 日志里绝对不要打印密码，所以这里不输出 */
    @Override
    public String toString() {
        return "LoginRequest{username='" + username + "'}";
    }
}
