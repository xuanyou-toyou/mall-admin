package demo.mall.service;

import demo.mall.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

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
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端测试：上传一张图 → 用接口返回的 URL 通过真实 HTTP 取回来。
 *
 * ============================================================
 * 为什么必须新写这一条（原来的测试漏在哪）
 * ============================================================
 *
 * 已有的 FileServiceTest 全部是「直接调 Service」：
 *   fileService.upload(file) → 得到 /uploads/2026/11/22/xxx.png
 *   → 再调用 fileService.getRootDir() 反推磁盘路径 → 断言文件存在
 *
 * 这条链路的致命盲区：**它根本不过 HTTP 层**。
 * 于是下面这类故障它能全绿通过：
 *
 *   FileService 返回 /uploads/xxx.png
 *   WebConfig 映射的是 /images/**（或者前缀写成 /upload）
 *   → 接口 200、URL 看着完全正常，但浏览器打开图片 404
 *
 * 这正是「/uploads 前缀写了两遍」的真实后果：改一处，URL 和映射对不上，
 * 而老测试是用磁盘路径反推的，永远测不出来。
 *
 * 所以这里必须：
 *   ① @SpringBootTest(webEnvironment = RANDOM_PORT) 起真 Tomcat
 *   ② 真的发 multipart 请求拿 URL
 *   ③ 真的 GET 那个 URL，断言 200 + Content-Type 是 image/*
 *   ④ 断言取回的字节和上传的字节一致（不是只看到一个 200 就算过）
 *
 * ============================================================
 * 为什么不用 MockMvc
 * ============================================================
 * MockMvc 不经过 Servlet 容器，也不走 Spring MVC 的 ResourceHttpRequestHandler
 * 那套静态资源解析链路（它只做 HandlerMapping 匹配后交给 mock 的请求/响应）。
 * 用它 GET /uploads/xxx 往往直接是 404（或者需要手动注册资源处理器），
 * 就算想办法绕过去，也证明不了「真实部署时这个 URL 能访问」。
 * 结论：MockMvc 在这里测出来的是假绿，必须真容器。
 *
 * ============================================================
 * 为什么 token 是本地签的而不是走注册登录接口
 * ============================================================
 * 这个测试关心的是「上传 + 静态资源访问」，不是认证流程（认证已有独立测试）。
 * 用 JwtUtil 直接签一个 token 可以少两次 HTTP 往返、也不往 user 表插脏数据，
 * 但仍然真的经过 AuthInterceptor 验签 —— 拦截器该做的事一样没少。
 * userId 用 1 就行：拦截器只验签，不查库确认用户存在。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mall.upload.dir=target/e2e-uploads")
@DisplayName("端到端：上传后通过返回的 URL 取图")
class UploadEndToEndTest {

    /** 1x1 透明 PNG，67 字节，合法的真 PNG（不是随便几个字节）。 */
    private static final String PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private FileService fileService;

    private HttpClient http;

    @BeforeEach
    void setUp() {
        http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        // 只清测试专用目录，绝不碰真实 uploads/
        Path root = fileService.getRootDir();
        if (!Files.exists(root)) {
            return;
        }
        if (!"e2e-uploads".equals(root.getFileName().toString())) {
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

    @Test
    @DisplayName("上传 PNG → 返回 URL → GET 该 URL → 200 + image/png + 字节一致")
    void uploadThenFetchOverHttp() throws Exception {
        System.out.println();
        System.out.println("========== 端到端：上传 → HTTP 取图 ==========");

        byte[] png = Base64.getDecoder().decode(PNG_BASE64);
        assertTrue(png.length > 8 && (png[0] & 0xFF) == 0x89 && png[1] == 'P',
                "测试素材必须是合法 PNG");

        // ------------------------------------------------------------
        // ① 上传（带 token，走 /api/products/upload）
        // ------------------------------------------------------------
        String token = jwtUtil.generateToken(1L, "e2e");
        String boundary = "----MallAdminE2E" + System.nanoTime();
        byte[] body = multipartBody(boundary, "pixel.png", "image/png", png);

        HttpRequest uploadRequest = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/products/upload"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> uploadResponse =
                http.send(uploadRequest, HttpResponse.BodyHandlers.ofString());
        System.out.println("  上传状态 : " + uploadResponse.statusCode());
        System.out.println("  上传响应 : " + uploadResponse.body());

        assertEquals(200, uploadResponse.statusCode(),
                "上传应成功，实际 " + uploadResponse.statusCode() + " body=" + uploadResponse.body());

        String url = extractDataField(uploadResponse.body());
        System.out.println("  返回 URL : " + url);

        assertTrue(url.startsWith(fileService.getUrlPrefix() + "/"),
                "URL 应以 " + fileService.getUrlPrefix() + "/ 开头，实际: " + url);

        // ------------------------------------------------------------
        // ② 只用返回的 URL 取图，不带任何 token
        //    （顺带证明 /uploads/** 不在 AuthInterceptor 的拦截范围内）
        // ------------------------------------------------------------
        HttpRequest getRequest = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        HttpResponse<byte[]> imageResponse =
                http.send(getRequest, HttpResponse.BodyHandlers.ofByteArray());
        String contentType = imageResponse.headers().firstValue("Content-Type").orElse("");
        System.out.println("  GET 状态 : " + imageResponse.statusCode());
        System.out.println("  Content-Type : " + contentType);
        System.out.println("  取回字节数 : " + imageResponse.body().length);

        // 这条断言是整个 P0-2 的核心：
        // 如果 WebConfig 的映射模式和这里的 URL 前缀对不上，这里会是 404
        assertEquals(200, imageResponse.statusCode(),
                "GET " + url + " 应 200；404 通常意味着 WebConfig 的映射前缀"
                        + "和 FileService 返回的 URL 前缀不一致，实际 "
                        + imageResponse.statusCode());

        assertTrue(contentType.startsWith("image/"),
                "Content-Type 应是 image/*，实际: " + contentType);
        assertEquals("image/png", contentType, "扩展名 .png 应被静态资源处理器识别为 image/png");

        assertArrayEquals(png, imageResponse.body(), "取回的字节必须和上传的一致");

        System.out.println("  ✅ URL 可访问、类型正确、字节一致");
    }

    /** 从 {"code":200,"data":"/uploads/...","message":"成功"} 里抠出 data。 */
    private static String extractDataField(String json) {
        Matcher m = Pattern.compile("\"data\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        assertTrue(m.find(), "响应体里找不到 data 字段: " + json);
        return m.group(1);
    }

    /**
     * 手搓 multipart/form-data（java.net.http 没有现成的 multipart publisher）。
     * 每行都必须用 CRLF，用 \n 会导致容器解析失败。
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
}
