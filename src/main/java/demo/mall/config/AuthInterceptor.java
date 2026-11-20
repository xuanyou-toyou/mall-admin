package demo.mall.config;

import demo.mall.common.BizException;
import demo.mall.common.ResultCode;
import demo.mall.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录鉴权拦截器。
 *
 * ============================================================
 * 它是干什么的
 * ============================================================
 *
 * 在请求到达 Controller 之前，先检查「这个请求带没带有效的 token」。
 *
 *   带了且有效  → 放行，顺便把 userId 塞进 request，Controller 可以直接用
 *   没带或无效  → 直接拦下来，抛异常，请求根本进不到 Controller
 *
 * ============================================================
 * 拦截器 vs 过滤器
 * ============================================================
 *
 *                     请求进来
 *                        ↓
 *   ① Filter（Servlet 层）     —— 只知道 URL，不知道要调哪个 Controller 方法
 *                        ↓
 *   ② DispatcherServlet        —— Spring 的总入口
 *                        ↓
 *   ③ Interceptor（MVC 层）    —— 能拿到 HandlerMethod，知道具体调哪个方法
 *                        ↓
 *   ④ Controller
 *
 * 为什么用拦截器做鉴权？
 *   ① 能精确控制「哪些接口放行」，配合 WebConfig 里的 excludePathPatterns
 *   ② 天然支持 Spring 依赖注入（JwtUtil 直接构造器注入进来）
 *   ③ 抛出的异常会被 @RestControllerAdvice 统一处理，和业务异常走同一套格式
 *
 * ============================================================
 * token 怎么传
 * ============================================================
 *
 * 前端登录后拿到 token，之后每个请求都加一个请求头：
 *
 *      Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
 *                     ↑
 *                  注意这里有个空格
 *
 * "Bearer " 一共 7 个字符（6 个字母 + 1 个空格），
 * 所以下面用 substring(7) 截取真正的 token。
 *
 * ⚠️ 判断前缀时必须写 "Bearer " 带空格：
 *    如果只写 "Bearer"，那么 "BearerX123" 这种畸形请求也能通过判断，
 *    然后 substring(7) 会截到错误的位置。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /**
     * JWT 工具，用来校验 token 和解析出 userId。
     * 通过构造器注入 —— 拦截器本身是 Spring Bean，所以可以正常注入依赖。
     */
    private final JwtUtil jwtUtil;

    public AuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    /**
     * 在 Controller 方法执行「之前」调用。
     *
     * 返回值决定去留：
     *   true   → 放行，继续往 Controller 走
     *   false  → 中断，不再往下执行（后面还要自己写响应体，比较麻烦）
     *
     * 这里选择「抛异常」而不是「return false」：
     *   抛 BizException(ResultCode.UNAUTHORIZED) 会被全局异常处理器捕获，
     *   自动转成 HTTP 401 + {"code":401,"message":"未登录或登录已过期"}，
     *   和登录接口失败的格式完全一致，前端不用为鉴权失败单独写解析逻辑。
     *
     * @param request  请求对象，可以取请求头、设置属性
     * @param response 响应对象，这里用不到（异常由全局处理器写响应）
     * @param handler  即将执行的处理器（就是那个 Controller 方法），这里用不到
     * @return true 表示放行
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {

        // ① 从请求头取 Authorization
        //    没有这个头时返回 null，不会抛异常
        String header = request.getHeader("Authorization");

        // ② 判空 + 判前缀
        //    注意 "Bearer " 后面必须带空格：7 个字符（6 字母 + 1 空格）
        //    如果只判 "Bearer"，下面 substring(7) 会截错位置
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        // ③ 截掉 "Bearer " 这 7 个字符，拿到真正的 token 字符串
        //    然后验签 + 检查是否过期，任何一项不通过都抛异常
        String token = header.substring(7);
        if (!jwtUtil.validateToken(token)) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        // ④ 验签通过后，把 token 里的 userId 取出来，存进 request 属性
        //
        //    为什么存 request 属性？
        //      request 是「一次请求内共享」的容器，拦截器塞进去，
        //      Controller 里用 request.getAttribute("userId") 就能拿到。
        //      这样 Controller 不需要再解析一次 token。
        //
        //    ⚠️ 这里的 userId 是从 token 里解出来的，
        //       前端无法伪造（改 payload 签名就对不上），
        //       所以可以信任它，用来做「当前登录用户」的标识。
        request.setAttribute("userId", jwtUtil.getUserId(token));

        // 返回 true 表示放行，继续执行后面的拦截器和 Controller
        return true;
    }
}
