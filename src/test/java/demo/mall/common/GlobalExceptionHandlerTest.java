package demo.mall.common;

import demo.mall.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证「上传超过 5MB → 400 而不是 500」。
 *
 * ============================================================
 * 为什么这里有两种测法（各测各的，都不多余）
 * ============================================================
 *
 * ① 直接调 handler 方法
 *    测的是「Handler 本身的契约」：给定这个异常，必须返回 400 + 指定文案。
 *    快、稳定、不启动容器。
 *    但它证明不了「异常真的会被路由到这个 handler」——
 *    万一 Spring 先被别的 handler 接走了，这条照样绿。
 *
 * ② 起真实 Tomcat，发一个真的 5MB+ multipart 请求
 *    测的是「整条链路」：容器解析 → 抛异常 → 异常解析器 → 选中本 handler → 写响应。
 *    这条才是真正能证明修复生效的测试。
 *
 * ============================================================
 * 为什么不用 MockMvc（关键决策）
 * ============================================================
 *
 * MockMvc 的 multipart 请求是「已经解析好的」：
 *   MockMvcRequestBuilders.multipart("/api/products/upload")
 *       .file(new MockMultipartFile("file", "big.png", "image/png", bigBytes))
 *
 * 这一行只是把一个现成的 MockMultipartFile 塞进 MockMultipartHttpServletRequest，
 * 全程没有任何人按 max-file-size 去量它 ——
 * 因为限额是 Servlet 容器（Tomcat）解析请求体时实施的，MockMvc 根本不经过容器。
 *
 * 结论：MockMvc 里传 100MB 也不会抛 MaxUploadSizeExceededException，
 * 写完只会得到一个「PASS」的假绿测试。所以这里必须起真容器（RANDOM_PORT）。
 *
 * ============================================================
 * 为什么上传目录要指向 target/test-uploads
 * ============================================================
 * 万一 handler 没生效、文件被真的收下了，也不能落到业务的 uploads/ 里。
 * 指到 target/ 下，天然被 .gitignore 忽略，用例结束再递归清掉。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mall.upload.dir=target/test-uploads")
@DisplayName("GlobalExceptionHandler：客户端错误一律 400（上传超限 / 参数类型错误）")
class GlobalExceptionHandlerTest {

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * RANDOM_PORT 下容器启动后会往 Environment 里写 local.server.port。
     * 用 @Value 取而不是 @LocalServerPort，是为了少依赖一个包路径。
     */
    @Value("${local.server.port}")
    private int port;

    /** 单文件上限 5MB，和 application.properties / FileService 保持一致。 */
    private static final long MAX_SIZE = 5L * 1024 * 1024;

    /** 期望返回的文案（和 handler 里写的一字不差）。 */
    private static final String EXPECTED_MESSAGE = "上传文件超过大小限制（单文件最大 5MB）";

    @AfterEach
    void tearDown() throws IOException {
        Path root = Path.of("target/test-uploads").toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            return;
        }
        List<Path> paths;
        try (var stream = Files.walk(root)) {
            paths = stream.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path p : paths) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
                // 删不掉不影响测试结论
            }
        }
    }

    // ================================================================
    // ① handler 契约单测
    // ================================================================
    @Test
    @DisplayName("① 直接调 handler：HTTP 400 + code=400 + 指定文案")
    void handlerReturns400() {
        System.out.println();
        System.out.println("========== ① handler 单测 ==========");

        // 不需要 Spring：handler 方法就是普通方法，new 出来直接调
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        // 构造一个「最大 5MB」的超限异常，模拟 Spring 解析 multipart 时抛出来的那个
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(MAX_SIZE);

        ResponseEntity<Result<Void>> response = handler.handleMaxUploadSizeExceededException(ex);

        System.out.println("  HTTP 状态 : " + response.getStatusCode());
        assertNotNull(response.getBody());
        System.out.println("  code      : " + response.getBody().getCode());
        System.out.println("  message   : " + response.getBody().getMessage());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), "必须是 400，不能是 500");
        assertEquals(400, response.getBody().getCode());
        assertEquals(EXPECTED_MESSAGE, response.getBody().getMessage());
        assertNull(response.getBody().getData(), "失败响应不该有 data");

        System.out.println("  ✅ 400 + 文案正确，且不是 500");
    }

    // ================================================================
    // ② 真容器集成测试（核心用例）
    // ================================================================
    @Test
    @DisplayName("② 真 Tomcat：5MB+64KB 的 multipart 上传 → 400")
    void realOversizeUploadReturns400() throws Exception {
        System.out.println();
        System.out.println("========== ② 真容器集成测试 ==========");

        // 比上限多 64KB。
        // 为什么不干脆传 20MB：容器在 5MB 处就中断解析了，剩下的字节要由 Tomcat
        // 「吞掉」（max-swallow-size 默认 2MB）。超太多可能直接断连接、拿不到响应，
        // 那是测试环境问题，不是业务问题 —— 刚好越界最稳定。
        byte[] tooBig = new byte[(int) MAX_SIZE + 64 * 1024];
        System.out.println("  请求体内容大小 : " + tooBig.length + " 字节（上限 " + MAX_SIZE + "）");

        String boundary = "----MallAdminOversize" + System.nanoTime();
        byte[] body = multipartBody(boundary, "big.png", "image/png", tooBig);
        System.out.println("  完整请求体大小 : " + body.length + " 字节（含 multipart 头尾）");

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/products/upload"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("  响应状态 : " + response.statusCode());
        System.out.println("  响应体   : " + response.body());

        // 修复前这里会是 500（落到 Exception 兜底）；修复后必须是 400
        assertEquals(400, response.statusCode(),
                "超限上传应返回 400，实际 " + response.statusCode() + "，响应体=" + response.body());
        assertTrue(response.body().contains("\"code\":400"),
                "响应体应是统一格式且 code=400，实际: " + response.body());
        assertTrue(response.body().contains(EXPECTED_MESSAGE),
                "响应体应包含指定文案，实际: " + response.body());

        // 顺带确认：文件没有被收下（哪怕被收下也只会落在 target/test-uploads）
        Path root = Path.of("target/test-uploads").toAbsolutePath().normalize();
        if (Files.exists(root)) {
            try (var stream = Files.walk(root)) {
                long files = stream.filter(Files::isRegularFile).count();
                assertEquals(0, files, "超限文件不应该落盘");
            }
        }
        System.out.println("  ✅ 400 + 统一响应体 + 文件未落盘");
    }

    /**
     * 手搓 multipart/form-data 请求体。
     *
     * 为什么手搓：java.net.http.HttpClient 不像 Apache HttpClient 那样自带
     * MultipartBodyPublisher，需要自己拼。格式就是 RFC 7578：
     *
     *   --boundary CRLF
     *   Content-Disposition: form-data; name="file"; filename="big.png" CRLF
     *   Content-Type: image/png CRLF
     *   CRLF                      ← 空行，头结束、正文开始
     *   <字节> CRLF
     *   --boundary-- CRLF         ← 结尾多两个减号
     *
     * 注意每一行都要用 CRLF（\r\n），用 \n 的话 Tomcat 可能解析失败。
     */
    private static byte[] multipartBody(String boundary, String filename,
                                        String contentType, byte[] content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(content.length + 256);
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\""
                + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(content);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    // ================================================================
    // ③ 路径变量类型不匹配 → 400
    // ================================================================
    /**
     * 复现路径（为什么这条容易漏）：
     *
     *   GET /api/products/upload
     *        ↓ 没有精确匹配 "GET /api/products/upload" 的映射
     *        ↓ 但 @GetMapping("/{id}") 能匹配，于是 "upload" 被当成 id
     *        ↓ "upload" → Long 转换失败，抛 MethodArgumentTypeMismatchException
     *        ↓ 没有专门 handler 时落到 Exception 兜底 → 500
     *
     * 注意必须带 token：/api/** 被鉴权拦截器保护，
     * 不带 token 会在参数解析之前就 401，测不到这里的 400。
     *
     * 媒体类型这里没有特殊要求：GET 无 body，只要带头就行。
     */
    @Test
    @DisplayName("③ GET /api/products/upload（被 /{id} 吃掉）→ 400 而不是 500")
    void pathVariableTypeMismatchReturns400() throws Exception {
        System.out.println();
        System.out.println("========== ③ 参数类型不匹配 ==========");

        String token = jwtUtil.generateToken(1L, "e2e");
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/products/upload"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("  响应状态 : " + response.statusCode());
        System.out.println("  响应体   : " + response.body());

        // 修复前：MethodArgumentTypeMismatchException 落到 Exception 兜底 → 500
        assertEquals(400, response.statusCode(),
                "参数类型不匹配应 400，实际 " + response.statusCode() + "，响应体=" + response.body());
        assertTrue(response.body().contains("\"code\":400"), "code 应为 400: " + response.body());
        // 文案必须带参数名和实际值，否则前端不知道该改哪个参数
        assertTrue(response.body().contains("参数 'id' 的值 'upload' 格式不正确"),
                "message 应包含参数名和原始值，实际: " + response.body());

        System.out.println("  ✅ 400 + 有指向性的 message");
    }
}
