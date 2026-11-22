package demo.mall.config;

import demo.mall.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
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

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

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

    /**
     * 文件服务。
     *
     * 为什么这里要注入 FileService，而不是再读一次 mall.upload.dir？
     *   因为静态资源映射的目录必须和「文件实际落盘的目录」是同一个。
     *   如果两边各自解析一次配置，将来有人改了配置名、或者给 FileService
     *   加了自定义解析逻辑（比如支持 classpath:、支持 ~ 展开），
     *   就会出现「上传成功、URL 拼好了、但一访问 404」的割裂状态。
     *   直接从 FileService 拿已经算好的绝对路径，只有唯一事实来源。
     */
    private final FileService fileService;

    public WebConfig(AuthInterceptor authInterceptor, FileService fileService) {
        this.authInterceptor = authInterceptor;
        this.fileService = fileService;
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

    /**
     * 把 URL 路径映射到磁盘目录，让上传的图片能直接访问。
     *
     * ============================================================
     * 映射规则
     * ============================================================
     *
     *   GET /uploads/2026/11/21/xxx.jpg
     *        ↓  去掉 /uploads 前缀，拼到 location 后面
     *   文件 <mall.upload.dir>/2026/11/21/xxx.jpg
     *
     * addResourceHandler("/uploads/**")  对外暴露的 URL 模式
     * addResourceLocations("file:/.../uploads/")  真实目录
     *
     * ============================================================
     * 两个容易踩的坑
     * ============================================================
     *
     * ① location 必须以 / 结尾。
     *    写成 file:/E:/x/uploads（不带结尾斜杠）时，Spring 会把
     *    "2026/11/21/xxx.jpg" 直接拼成 ".../uploads2026/11/21/xxx.jpg"，
     *    结果就是全部 404，而且不报错，非常难查。
     *
     * ② 必须带 file: 前缀。
     *    不带前缀时 Spring 会当成 classpath 相对路径（等价于 classpath:），
     *    去 jar 里的 classes 下找，同样静默 404。
     *
     * 下面用 Path.toUri() 生成，它天然满足这两点（file:///E:/.../uploads/
     * 已经带了 file: 和结尾斜杠），比自己手拼 "file:" + path 可靠 ——
     * 手拼在 Windows 上还得处理反斜杠和盘符。
     *
     * ============================================================
     * 为什么 /uploads/** 天然免鉴权（这条要确认清楚）
     * ============================================================
     *
     * 鉴权只在 addInterceptors 里注册了 addPathPatterns("/api/**")，
     * 拦截范围就是「以 /api/ 开头的路径」。/uploads/** 以 /uploads/ 开头，
     * 不在 addPathPatterns 的匹配范围内，所以 AuthInterceptor 的 preHandle
     * 根本不会被调用 —— 不需要额外写 excludePathPatterns，
     * 写了反而是「在没拦的范围内再排除一次」，纯属噪音。
     *
     * 这是有意为之：商品图片要能直接给 <img src> 用，
     * 浏览器发图片请求时不会带 Authorization 头（也没法带），
     * 如果图片要鉴权，前端就得把每张图 fetch 成 blob 再转 URL，非常难用。
     *
     * ⚠️ 前提是上传目录里只放「公开可看」的内容。
     *    如果以后要传「用户身份证、合同附件」这类私有文件，
     *    绝不能放这个目录 —— 应该单独存一个私有目录，走需要鉴权的下载接口。
     *
     * ⚠️ 另一点：这里只做映射，不做「目录列表」。
     *    /uploads/ 本身访问不到文件列表（Spring 的资源处理器只按具体文件找），
     *    文件名是 32 位 UUID，别人猜不到别人的图。
     *    但要清楚这是「不可枚举」而不是「有权限控制」——
     *    拿到 URL 的人就能看，别把敏感东西放进来。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = fileService.getRootDir().toUri().toString();
        // 防御：toUri() 在目录不存在时不保证带结尾斜杠，补一下，避免坑 ①
        if (!location.endsWith("/")) {
            location = location + "/";
        }

        // 模式前缀从 FileService 拿，不在这里重写 "/uploads" 字面量。
        // 原因：这个前缀同时决定「上传返回的 URL」和「静态资源能访问的路径」，
        //   两处各写一遍 = 改一处就出现「接口 200 返回一个看着正常的 URL，但 GET 404」，
        //   而且用 getRootDir() 反推磁盘路径的老测试完全测不出来。
        // 现在只有 FileService.URL_PREFIX 一个来源，物理上不可能不一致。
        // 注意拼的是 "/uploads/**"，不是 "/uploads/*"：后者不匹配子目录里的文件。
        String pattern = fileService.getUrlPrefix() + "/**";
        log.info("静态资源映射: {} -> {}", pattern, location);
        registry.addResourceHandler(pattern).addResourceLocations(location);
    }
}
