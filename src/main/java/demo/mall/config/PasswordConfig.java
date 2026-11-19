package demo.mall.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密配置。
 *
 * 为什么只引入 spring-security-crypto 而不引入完整的 spring-boot-starter-security？
 *   完整版会自动给所有接口加上认证拦截，返回 401 和默认登录页，
 *   和我们自己的 Result 统一返回体冲突。
 *   我们只需要 BCrypt 这个加密工具，所以只引最小的那一个模块。
 *
 * PasswordEncoder 是 Spring Security 定义的接口，BCryptPasswordEncoder 是它的实现。
 * 把它注册成 Bean，AuthService 里 @Autowired 就能直接用。
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 代价因子（strength）默认 10，表示迭代 2^10 = 1024 轮。
        // 数字越大越安全，但也越慢：每 +1，耗时翻倍。
        // 10 是社区通用值，单次约 100 毫秒。
        return new BCryptPasswordEncoder(10);
    }
}
