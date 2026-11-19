package demo.mall.controller;

import demo.mall.common.Result;
import demo.mall.dto.LoginRequest;
import demo.mall.dto.LoginResponse;
import demo.mall.dto.RegisterRequest;
import demo.mall.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证接口。
 *
 * Controller 的职责只有三个，不多也不少：
 *   ① 定义路由（@PostMapping）
 *   ② 把请求体转成 Java 对象（@RequestBody）
 *   ③ 转发给 Service
 *
 * 校验、加密、查重、签发 token，统统不在这里写。
 */
@Tag(name = "认证", description = "注册、登录")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * POST /api/auth/register —— 用户注册
     *
     * 请求体：
     *   {
     *     "username": "zhangsan",
     *     "password": "123456",
     *     "nickname": "张三",
     *     "phone": "13800138000"
     *   }
     *
     * 成功：HTTP 200  {"code":200,"message":"成功","data":null}
     * 失败：HTTP 400  {"code":400,"message":"用户名已被注册","data":null}
     *
     * ------------------------------------------------------------------
     * 关于 @Valid
     * ------------------------------------------------------------------
     * 这个注解不能省。
     *
     * 它的作用是：让 Spring 在进入方法体之前，执行 RegisterRequest 里
     * 那些 @NotBlank / @Size / @Pattern 校验。
     *
     * 不写 @Valid 的话，那些注解就只是装饰，一个都不会生效 ——
     * 空的用户名、5 位密码、乱填的手机号都能直接进到 Service 里。
     *
     * 校验失败时 Spring 抛 MethodArgumentNotValidException，
     * 由 GlobalExceptionHandler 捕获，转成 HTTP 400 和我们自己的 Result 格式。
     *
     * ------------------------------------------------------------------
     * 为什么返回 Result<Void> 而不是 Result<User>
     * ------------------------------------------------------------------
     * 注册成功只需要告诉前端「成功了」，不需要把用户信息回传：
     *   - 密码是敏感字段，不回传最安全
     *   - 前端注册完一般会跳到登录页，不需要用户对象
     */
    @Operation(summary = "用户注册", description = "用户名和手机号不能重复，密码 6-32 位")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest req) {
        userService.register(req);
        return Result.ok();
    }

    /**
     * POST /api/auth/login —— 用户登录
     *
     * 请求体：
     *   {
     *     "username": "zhangsan",
     *     "password": "123456"
     *   }
     *
     * 成功：HTTP 200
     *   {
     *     "code": 200,
     *     "message": "成功",
     *     "data": {
     *       "token": "eyJhbGciOiJIUzUxMiJ9...",
     *       "tokenType": "Bearer",
     *       "expiresIn": 7200000,
     *       "userId": 5,
     *       "username": "zhangsan",
     *       "nickname": "张三"
     *     }
     *   }
     *
     * 失败：HTTP 401  {"code":401,"message":"未登录或登录已过期","data":null}
     *
     * ------------------------------------------------------------------
     * 注意：用户名不存在 和 密码错误，返回的是同一个 401 和同一条消息
     * ------------------------------------------------------------------
     * 这不是偷懒，是刻意设计。
     *
     * 如果分开返回「用户不存在」和「密码错误」，攻击者就能拿一个字典
     * 挨个试用户名，通过错误消息判断哪些用户名是有效的
     * —— 这叫「用户名枚举攻击」。
     *
     * 统一成「用户名或密码错误」，攻击者就无法区分这两种情况。
     *
     * ------------------------------------------------------------------
     * 前端拿到 token 之后怎么用
     * ------------------------------------------------------------------
     * 存起来（localStorage 或 Cookie），之后每个请求加一个请求头：
     *
     *   Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
     *
     * 服务端从请求头里取出来验签，就知道是谁在请求了。
     * 这一步的实现在「鉴权拦截器」里，是 W11 的最后一个教学点。
     */
    @Operation(summary = "用户登录", description = "成功返回 JWT token，用户名不存在和密码错误返回同样的 401")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok(userService.login(req));
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(Map.of("userId", userId));
    }
}
