package demo.mall.config;

import demo.mall.common.BizException;
import demo.mall.common.ResultCode;
import demo.mall.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public AuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {

        // ① 取请求头
        String header = request.getHeader("Authorization");

        // ② 判空 + 判前缀
        //    注意 "Bearer " 后面必须带空格：7 个字符（6 字母 + 1 空格）
        //    如果只判 "Bearer"，下面 substring(7) 会截错位置
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        // ③ 截取 token 并校验
        String token = header.substring(7);
        if (!jwtUtil.validateToken(token)) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        // ④ 取出 userId，放进 request 属性里，给后面的 Controller 用
        request.setAttribute("userId", jwtUtil.getUserId(token));

        return true;   // 返回 true 表示放行
    }
}