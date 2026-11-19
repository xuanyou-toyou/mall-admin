package demo.mall;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Scanner;

/**
 * BCrypt 行为演示 —— 不是为了测试正确性，是为了让你「看见」它长什么样。
 *
 * 运行方式：
 *   IDEA 里点类左边的绿色三角
 *   或者 mvn test -Dtest=PasswordDemoTest
 *
 * 这个类里的 System.out 是刻意的 —— 演示代码要打印给人看，
 * 业务代码里请用 log.info。
 */
@DisplayName("BCrypt 行为演示（打印用）")
class PasswordDemoTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(10);

    @Test
    @DisplayName("跑一遍，看真正的输入输出")
    void showHowBcryptWorks() {


        String rawPassword = "928127";
        System.out.println();
        System.out.println("========== 1. 原始密码 ==========");
        System.out.println("用户输入：" + rawPassword);
        System.out.println("长度：" + rawPassword.length() + " 个字符");

        System.out.println();
        System.out.println("========== 2. 加密两次，看结果一样吗 ==========");
        long t1 = System.nanoTime();
        String hash1 = encoder.encode(rawPassword);
        long cost1 = (System.nanoTime() - t1) / 1_000_000;

        String hash2 = encoder.encode(rawPassword);

        System.out.println("第一次：" + hash1);
        System.out.println("         长度 " + hash1.length() + " 字符，耗时 " + cost1 + " 毫秒");
        System.out.println("第二次：" + hash2);
        System.out.println("长度 " + hash2.length() + " 字符");
        System.out.println();
        System.out.println("两次结果一样吗？" + hash1.equals(hash2));
        System.out.println(">>> 不一样！这就是「盐」的作用：");
        System.out.println(">>> 同样输入 123456，每次都产生不同的哈希，防彩虹表。");

        System.out.println();
        System.out.println("========== 3. 字符串拆解 ==========");
        System.out.println("哈希结构：" + hash1);
        String[] parts = hash1.split("\\$");
        // BCrypt 的格式是 $版本$代价$盐+哈希值
        // 盐固定 22 字符，剩下的 31 字符才是哈希值，两者拼在一起，所以只有 4 段
        String saltAndHash = parts[3];
        System.out.println("  版本       : " + parts[1] + "   （2a 表示 BCrypt 的版本）");
        System.out.println("  代价因子   : " + parts[2] + "   → 迭代 2^" + parts[2] + " = "
                + (1 << Integer.parseInt(parts[2])) + " 轮");
        System.out.println("  盐         : " + saltAndHash.substring(0, 22));
        System.out.println("  哈希值     : " + saltAndHash.substring(22));
        System.out.println("  >>> 盐 22 字符 + 哈希 31 字符 = 53 字符");
        System.out.println("  >>> 加上 \"$2a$10$\" 这 7 个字符，总共 60 字符");

        System.out.println();
        System.out.println("========== 4. 比对：正确密码 vs 错误密码 ==========");
        System.out.println("库里的哈希：" + hash1);
        System.out.println();

        boolean ok1 = encoder.matches("123456", hash1);
        System.out.println("输入 \"123456\"   → " + ok1 + "   ← 应该 true");

        boolean ok2 = encoder.matches("1234567", hash1);
        System.out.println("输入 \"1234567\"  → " + ok2 + "   ← 差一个字符就 false");

        boolean ok3 = encoder.matches("123456", hash2);
        System.out.println("输入 \"123456\" 比对第二个哈希 → " + ok3 + "  ← 也是 true");
        System.out.println(">>> 注意：两个哈希值不同，但都能验证同一个密码。");

        System.out.println();
        System.out.println("========== 5. 证明它真的不能反推 ==========");
        System.out.println("哈希值：" + hash1);
        System.out.println(">>> 没有 matches() 以外的任何方法能把上面这串还原成 \"123456\"。");
        System.out.println(">>> 这就是「不可逆」：只能比对，不能解密。");

        System.out.println();
        System.out.println("========== 6. 代价因子对比（慢是故意的）==========");
        long start = System.nanoTime();
        String strong = new BCryptPasswordEncoder(12).encode(rawPassword);
        long cost12 = (System.nanoTime() - start) / 1_000_000;
        System.out.println("代价因子 10 → 约 " + cost1 + " 毫秒");
        System.out.println("代价因子 12 → 约 " + cost12 + " 毫秒  （迭代次数 ×4，耗时也约 ×4）");
        System.out.println(">>> 攻击者每猜一个密码都要等这么久，暴力破解就不可行了。");
        System.out.println(">>> 而正常用户登录只算一次，0.1 秒完全无感。");
        System.out.println();
        System.out.println(">>> 参数 12 生成的哈希：" + strong);
        System.out.println();
    }
}
