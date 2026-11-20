package demo.mall.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 的全局配置。
 *
 * ============================================================
 * 这个类在干什么
 * ============================================================
 *
 * Spring Boot 会自动配置好 MVC 的一堆默认行为（静态资源、消息转换器、
 * 参数解析器等等）。但如果你想改默认行为、或者加自己的组件，就实现
 * WebMvcConfigurer 接口，重写感兴趣的那个方法即可。
 *
 * WebMvcConfigurer 里所有方法都是 default 的（Java 8+ 接口默认方法），
 * 所以只需要重写你要的那个，其他不用管。
 *
 * 常见的可重写方法：
 *   addInterceptors        加拦截器            ← 这里用到的
 *   addCorsMappings        配跨域
 *   addResourceHandlers    配静态资源映射
 *   configureMessageConverters  改 JSON 转换器
 *   addArgumentResolvers   加自定义参数解析器
 *
 * ============================================================
 * @Configuration 是什么
 * ============================================================
 *
 * 标记这是一个「配置类」，Spring 启动时会扫描它并执行里面的逻辑。
 * 它本身也是一个 Bean（内部被 @Component 元注解标记了）。
 *
 * 注意：Spring Boot 4 里 @Configuration 默认是 proxyBeanMethods = true，
 * 意思是内部 @Bean 方法之间有调用时会走代理，保证返回同一个实例。
 * 这个类里没有 @Bean 方法，所以无所谓。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 鉴权拦截器。
     *
     * 注意这里是「注入拦截器」，而不是「new 一个拦截器」。
     *
     * 为什么要注入？
     *   拦截器本身是 @Component，Spring 已经创建好了实例，
     *   而且它的 JwtUtil 依赖也已经注入完成。
     *   如果自己 new，JwtUtil 就是 null，一跑就 NPE。
     */
    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    /**
     * 注册拦截器，并指定「拦哪些路径、放行哪些路径」。
     *
     * ============================================================
     * 三个方法的链式调用
     * ============================================================
     *
     *   addInterceptor(拦截器)      必需：要注册的拦截器实例
     *   addPathPatterns(路径...)    可选：拦哪些（不写默认拦所有）
     *   excludePathPatterns(...)    可选：放行哪些（在拦截范围内再排除）
     *
     * 最终效果 = addPathPatterns 命中 且 不在 excludePathPatterns 里
     *
     * ============================================================
     * 路径通配符规则
     * ============================================================
     *
     *   /api/auth/**    匹配 /api/auth 下任意层级
     *                   → /api/auth/login、/api/auth/register、/api/auth/x/y 都匹配
     *
     *   /api/ping       精确匹配这一个路径
     *
     *   /api/hello/**   /api/hello 下任意层级
     *
     *   /**             所有路径
     *
     * ============================================================
     * 为什么放行这几个
     * ============================================================
     *
     *   /api/auth/**    登录、注册 —— 用户还没登录，当然不能要求带 token
     *                   否则就是死锁：想登录得先登录
     *
     *   /api/ping       健康检查 —— 运维探测服务是否活着，不该需要鉴权
     *
     *   /api/hello/**   开发期的测试接口，上线前应该删掉
     *
     * ⚠️ 注意 /api/auth/** 是「整个认证模块」放行。
     *    如果以后往 AuthController 里加了需要登录的接口（比如「修改密码」），
     *    它也会被放行 —— 这是个隐患。
     *    那时候应该改成精确放行 /api/auth/login 和 /api/auth/register。
     *
     * @param registry 拦截器注册表，由 Spring 传进来
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                // 拦截 /api 下所有请求
                .addPathPatterns("/api/**")
                // 放行：登录注册、健康检查、测试接口
                .excludePathPatterns("/api/auth/**", "/api/ping", "/api/hello/**");
    }
}
