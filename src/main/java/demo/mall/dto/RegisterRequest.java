package demo.mall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。
 *
 * 为什么要有 DTO，不直接用 User 实体收参数？
 *
 *   如果用 User 收参数，前端能直接传 id、status、created_at 这些字段，
 *   等于把数据库结构暴露给外部，还可能被恶意传值（比如自己设 status=999）。
 *
 *   DTO 只暴露「允许前端传的字段」，多余的一概接不到。
 *
 * ============================================================
 * 关于这些校验注解
 * ============================================================
 *
 * 下面用的 @NotBlank / @Size / @Pattern 是 Jakarta Bean Validation 的注解。
 *
 * 它们本身不校验任何东西 —— 需要配合 Controller 参数上的 @Valid 注解，
 * Spring 才会在进入方法之前执行校验，失败抛 MethodArgumentNotValidException，
 * 然后被我们的 GlobalExceptionHandler 捕获成 400。
 *
 * 也就是说：
 *   @Valid 加在 Controller 的参数上 → 这些注解才生效
 *   不加 @Valid → 这些注解就是装饰，什么都不会发生
 */
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 20, message = "用户名长度必须在 4-20 位之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度必须在 6-32 位之间")
    private String password;

    /** 昵称可选，不填就是 null */
    @Size(max = 20, message = "昵称不能超过 20 个字")
    private String nickname;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

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

    /** 不输出 password */
    @Override
    public String toString() {
        return "RegisterRequest{username='" + username + '\''
                + ", nickname='" + nickname + '\''
                + ", phone='" + phone + '\''
                + '}';
    }
}
