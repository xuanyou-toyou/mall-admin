package demo.mall.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 单元测试。
 *
 * 这个类不需要 Spring 容器，直接 new 出来测（纯单元测试）。
 * 因为 JwtUtil 只依赖两个构造参数，没有别的依赖。
 */
@DisplayName("JwtUtil 生成 / 解析 / 校验")
class JwtUtilTest {

    /** 32 字节以上的密钥（HS256 要求） */
    private static final String SECRET = "mall-admin-jwt-secret-key-please-change-in-production-0123456789";

    /** 2 小时 */
    private static final long TWO_HOURS = 2 * 60 * 60 * 1000L;

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, TWO_HOURS);

    @Test
    @DisplayName("生成 → 解析：userId 和 username 都对得上")
    void generateThenParse() {
        System.out.println();
        System.out.println("========== 1. 生成 token ==========");

        String token = jwtUtil.generateToken(42L, "zhangsan");
        System.out.println("token = " + token);
        System.out.println("长度  = " + token.length() + " 字符");

        System.out.println();
        System.out.println("========== 2. 拆开看三段 ==========");
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT 必须是三段");
        System.out.println("段数 = " + parts.length + " ✅");

        String header = decodeBase64(parts[0]);
        String payload = decodeBase64(parts[1]);
        System.out.println("header  = " + header);
        System.out.println("payload = " + payload);
        System.out.println("signature = " + parts[2]);
        System.out.println();
        System.out.println(">>> 注意：header 和 payload 是明文，任何人都能解码看");
        System.out.println(">>> 这就是为什么 payload 里绝对不能放密码");

        System.out.println();
        System.out.println("========== 3. 解析回来 ==========");
        Claims claims = jwtUtil.parseToken(token);

        System.out.println("subject   = " + claims.getSubject() + "   （userId）");
        System.out.println("username  = " + claims.get("username"));
        System.out.println("issuedAt  = " + claims.getIssuedAt());
        System.out.println("expiration= " + claims.getExpiration());

        assertEquals("42", claims.getSubject());
        assertEquals("zhangsan", claims.get("username"));
        assertEquals(42L, jwtUtil.getUserId(token));
        System.out.println();
        System.out.println("✅ 解析正确，userId = " + jwtUtil.getUserId(token));
    }

    @Test
    @DisplayName("有效 token → validate 返回 true")
    void validToken() {
        String token = jwtUtil.generateToken(1L, "alice");
        assertTrue(jwtUtil.validateToken(token));
        System.out.println();
        System.out.println("✅ 有效 token 校验通过");
    }

    @Test
    @DisplayName("被篡改的 token → validate 返回 false")
    void tamperedToken() {
        System.out.println();
        System.out.println("========== 篡改测试 ==========");

        String token = jwtUtil.generateToken(1L, "alice");
        String[] parts = token.split("\\.");

        // 把 payload 换成伪造的：userId 改成 999
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"999\",\"username\":\"hacker\"}".getBytes(StandardCharsets.UTF_8));

        String tampered = parts[0] + "." + forgedPayload + "." + parts[2];
        System.out.println("原始 token : " + token);
        System.out.println("篡改后 token: " + tampered);
        System.out.println("（把 payload 里的 subject 从 1 改成 999，签名保持不变）");

        assertFalse(jwtUtil.validateToken(tampered), "篡改过的 token 必须失效");
        assertThrows(JwtException.class, () -> jwtUtil.parseToken(tampered),
                "解析篡改的 token 应该抛 JwtException");

        System.out.println();
        System.out.println("✅ 篡改被发现，throw JwtException，validate 返回 false");
        System.out.println(">>> 这就是签名的作用：内容看得见，但改了就对不上");
    }

    @Test
    @DisplayName("过期的 token → validate 返回 false")
    void expiredToken() {
        System.out.println();
        System.out.println("========== 过期测试 ==========");

        // 造一个「签发即过期」的 JwtUtil：有效期 -1000 毫秒
        JwtUtil expiredUtil = new JwtUtil(SECRET, -1000L);
        String expiredToken = expiredUtil.generateToken(1L, "alice");

        System.out.println("token（签发时就已过期）= " + expiredToken);

        assertFalse(expiredUtil.validateToken(expiredToken), "过期 token 必须失效");
        assertThrows(JwtException.class, () -> expiredUtil.parseToken(expiredToken));

        System.out.println();
        System.out.println("✅ 过期 token 被拒绝");
        System.out.println(">>> 这也解释了为什么过期时间写在 payload 里却改不了：");
        System.out.println(">>> 改了就破坏签名，服务端一眼识破");
    }

    @Test
    @DisplayName("换个密钥的 token → 验签失败")
    void wrongKey() {
        System.out.println();
        System.out.println("========== 换密钥测试 ==========");

        JwtUtil otherUtil = new JwtUtil(
                "another-secret-key-at-least-32-bytes-long-0123456789", TWO_HOURS);
        String tokenFromOther = otherUtil.generateToken(1L, "alice");

        assertFalse(jwtUtil.validateToken(tokenFromOther), "别的密钥签的 token 不能通过");
        System.out.println("✅ 别的服务签的 token 无法通过本服务验签");
        System.out.println(">>> 这就是「只有服务端有密钥」的意义");
    }

    private static String decodeBase64(String segment) {
        return new String(Base64.getUrlDecoder().decode(segment), StandardCharsets.UTF_8);
    }
}
