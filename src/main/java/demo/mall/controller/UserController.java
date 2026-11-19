package demo.mall.controller;

import demo.mall.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户相关接口。
 *
 * 这个 Controller 的所有接口都需要登录 —— 因为它们不在
 * WebConfig 的 excludePathPatterns 里，会被 AuthInterceptor 拦截。
 *
 * 也就是说，能进到这里，说明 token 一定已经验过了。
 */
@Tag(name = "用户", description = "需要登录才能访问")
@RestController
@RequestMapping("/api")
public class UserController {

    /**
     * GET /api/users/me —— 查当前登录用户
     *
     * 这个接口的价值在于「证明拦截器真的生效了」：
     *
     *   不带 token  → 401（请求根本没进到这个方法）
     *   带对的 token → 200，而且返回的 userId 是从 token 里解出来的
     *
     * userId 是 AuthInterceptor 在 preHandle 里塞进 request 属性的：
     *   request.setAttribute("userId", jwtUtil.getUserId(token));
     *
     * 所以这里 request.getAttribute("userId") 拿到的值，
     * 一定是「token 里写着的那个 userId」，不是前端随便传的。
     */
    @Operation(summary = "查询当前登录用户", description = "从 token 里解析出 userId，证明鉴权生效")
    @GetMapping("/users/me")
    public Result<Map<String, Object>> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("message", "能进到这个方法，说明 token 有效");
        return Result.ok(data);
    }
}
