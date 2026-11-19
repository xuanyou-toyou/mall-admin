package demo.mall.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类：生成、解析、校验 token。
 *
 * ============================================================
 * JWT 是什么（先看这段，再看代码）
 * ============================================================
 *
 * 一段 JWT 长这样，三段用 "." 隔开：
 *
 *   eyJhbGciOiJIUzI1NiJ9 . eyJzdWIiOiIxIiwidXNlcm5hbWUiOiJ6aGFuZ3NhbiJ9 . 4kR2mQ...
 *         header                        payload                          signature
 *
 * header    — 说明用的什么算法（HS256），Base64 编码，**明文**
 * payload   — 放业务数据（userId、username、过期时间），Base64 编码，**明文**
 * signature — 用密钥对前两段算出来的签名，**无法伪造**
 *
 * 关键认知：
 *   1. 前两段是 Base64 不是加密，任何人拿去 https://jwt.io 都能看到内容
 *      → 所以 payload 里绝对不能放密码
 *   2. 安全性来自第三段签名：只有服务端有密钥，别人改了 payload 就算不出对的签名
 *   3. 服务端不存任何东西（无状态），每次请求靠验签就能确认身份
 */
@Component
public class JwtUtil {

    /**
     * 签名密钥。
     *
     * 从 application.properties 的 jwt.secret 读进来。
     *
     * 为什么不用硬编码？
     *   硬编码的密钥会提交到 Git，等于把钥匙公开挂在网上。
     *   真实项目应放环境变量或配置中心。
     *
     * 为什么至少 32 字节？
     *   HS256 = HMAC + SHA256，SHA256 输出 256 位，
     *   密钥短于 256 位会让安全强度打折，jjwt 会直接抛异常拒绝。
     */
    private final SecretKey secretKey;

    /** token 有效期（毫秒），从 application.properties 的 jwt.expiration 读 */
    private final long expiration;

    /**
     * 构造器注入。
     *
     * @param secret     jwt.secret 的值
     * @param expiration jwt.expiration 的值
     *
     * Keys.hmacShaKeyFor(byte[]) 会把字节数组包成一个 HMAC-SHA 用的 SecretKey，
     * 内部会检查长度是否达到 32 字节，不够会抛 WeakKeyException。
     */
    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration}") long expiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    // ============================================================
    //  生成 token —— 这一份先给你看，下一份你自己写
    // ============================================================

    /**
     * 生成 token。
     *
     * @param userId   用户 id，放在 JWT 标准的 subject 字段里
     * @param username 用户名，放自定义 claim
     * @return 三段式字符串
     */
    public String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + expiration);

        return Jwts.builder()
                // subject 是 JWT 规范里的标准字段，习惯用来放主体标识（这里是用户 id）
                .subject(String.valueOf(userId))
                // claim 放自定义字段。注意：这里放的内容别人能看到，所以别放密码
                .claim("username", username)
                // 签发时间，JWT 标准字段
                .issuedAt(now)
                // 过期时间，JWT 标准字段。解析时 jjwt 会自动检查，过期直接抛异常
                .expiration(expiresAt)
                // 用密钥签名。指定了密钥，jjwt 会自动推断出 HS256 算法
                .signWith(secretKey)
                // 拼成 xxx.yyy.zzz 的字符串
                .compact();
    }


    /**
     * 解析 token，取出里面的所有声明（claims）。
     *
     * 你要做的事：
     *   1. 用 Jwts.parser() 创建一个解析器
     *   2. 告诉它用哪个密钥验证签名 —— 方法名是 verifyWith(secretKey)
     *   3. build() 之后调用 parseSignedClaims(token)
     *   4. 从结果里 getPayload() 拿到 Claims 对象返回
     *
     * 需要捕获的异常：
     *   - 签名不对或格式错误 → JwtException
     *   - token 过期        → ExpiredJwtException（它是 JwtException 的子类）
     *
     * 方法签名建议：
     *   public Claims parseToken(String token)
     *   解析失败时抛 JwtException，交给 GlobalExceptionHandler 或调用方处理
     *
     * 提示：这些类已经在文件顶部 import 了，直接用。
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 校验 token 是否有效（签名对 + 没过期）。
     *
     * 你要做的事：
     *   调用 parseToken，成功返回 true，捕获 JwtException 返回 false。
     *
     * 注意：不要写成 try { parseToken(t); return true; } catch (Exception e) { return false; }
     *       捕获 Exception 太宽了，会把代码 bug 也当成「token 无效」吞掉。
     *       只捕获 JwtException。
     *
     * 方法签名建议：
     *   public boolean validateToken(String token)
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException e) {              // 只捕获 JwtException
            return false;
        }
    }

    /**
     * 从 token 里取 userId 的便捷方法（这个我写好了，你可以参考它的风格）。
     *
     * subject 存的是字符串，取出来要转成 Long。
     */
    public Long getUserId(String token) {
        return Long.valueOf(parseToken(token).getSubject());
    }

    /**
     * token 有效期（毫秒）。
     *
     * 给登录接口用，让 LoginResponse.expiresIn 和实际签发时的有效期保持一致。
     * 不这样做的话，代码里写死一个 7200000，改了配置这里就骗前端了。
     */
    public long getExpiration() {
        return expiration;
    }
}
