package demo.mall.service;

import demo.mall.common.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileService 上传校验与安全性测试。
 *
 * ============================================================
 * 为什么用 @SpringBootTest 而不是 new FileService("...")
 * ============================================================
 *
 * 这个类本身没有复杂依赖，理论上直接 new 更快。但走 Spring 容器能多验证一件事：
 * 配置文件里 mall.upload.dir 这个 key 真的能被 @Value 读到。
 * 如果 key 拼错（比如写成 mall.upload.dir、mall.upload-dir 写混），
 * 直接 new 的测试照样全绿，线上却全存到默认的 uploads 里 —— 这种坑只有走容器才暴露。
 *
 * 下面的 properties 把上传目录改到 target/test-uploads：
 *   ① 测试产物落在 target/ 下，天然被 .gitignore 忽略，不会误提交；
 *   ② 不污染真实业务目录 uploads/；
 *   ③ 每个用例结束统一清理，跑完不留垃圾。
 */
@SpringBootTest(properties = "mall.upload.dir=target/test-uploads")
@DisplayName("FileService 图片上传校验与安全")
class FileServiceTest {

    @Autowired
    private FileService fileService;

    /** 单文件上限，和 FileService.MAX_FILE_SIZE 保持一致。 */
    private static final long MAX_SIZE = 5L * 1024 * 1024;

    /** 用一条固定内容当「图片字节」，内容本身不重要，落盘后比对用。 */
    private static byte[] fakeImageBytes() {
        return "FAKE-IMAGE-CONTENT-0123456789".getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 把接口返回的 URL 还原成磁盘路径。
     *
     * /uploads/2026/11/21/abc.jpg
     *   ↓ 去掉 /uploads 前缀
     * <root>/2026/11/21/abc.jpg
     *
     * 能这么还原，本身就说明「URL 和落盘路径」是一致的，这也是要断言的点之一。
     */
    private Path physicalPathOf(String url) {
        assertTrue(url.startsWith("/uploads/"), "返回的 URL 必须以 /uploads/ 开头: " + url);
        return fileService.getRootDir().resolve(url.substring("/uploads/".length()));
    }

    /**
     * 每个用例后清理落盘文件。
     *
     * 只删 target/test-uploads —— 先确认目录名，再做递归删除，
     * 避免将来有人改了 properties 后，这里把真实的 uploads/ 业务图片删掉。
     */
    @AfterEach
    void tearDown() throws IOException {
        Path root = fileService.getRootDir();
        if (!Files.exists(root)) {
            return;
        }
        if (!"test-uploads".equals(root.getFileName().toString())) {
            System.out.println("  ⚠️ 上传根目录不是测试目录，跳过递归清理: " + root);
            return;
        }
        List<Path> paths;
        // try-with-resources：Files.walk 返回的 Stream 持有目录句柄，必须关
        try (var stream = Files.walk(root)) {
            // 倒序 = 先删文件、再删空目录（父目录在子目录之后被访问）
            paths = stream.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path p : paths) {
            try {
                // 文件直接删；空目录也能被 deleteIfExists 删掉
                Files.deleteIfExists(p);
            } catch (DirectoryNotEmptyException e) {
                // 目录里还有东西（理论上不会走到），保留即可，不影响测试结论
            }
        }
    }

    // ================================================================
    // ① 正常上传 jpg
    // ================================================================
    @Test
    @DisplayName("① 正常上传 jpg：返回 URL，文件真的落盘")
    void uploadJpg() throws IOException {
        System.out.println();
        System.out.println("========== ① 正常上传 jpg ==========");

        byte[] content = fakeImageBytes();
        // 故意用大写 .JPG：验证扩展名被转成小写后仍能过白名单，
        // 且最终 URL 里的扩展名是小写（避免同一张图出现 .JPG/.jpg 两种 URL）
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.JPG", "image/jpeg", content);

        String url = fileService.upload(file);
        System.out.println("  返回 URL : " + url);

        assertNotNull(url);
        assertTrue(url.startsWith("/uploads/"), "URL 应以 /uploads/ 开头");
        assertTrue(url.endsWith(".jpg"), "扩展名应统一转小写: " + url);
        // 形如 /uploads/2026/11/21/xxx.jpg —— 必须按日期分了三级目录
        assertTrue(url.matches("^/uploads/\\d{4}/\\d{2}/\\d{2}/[0-9a-f]{32}\\.jpg$"),
                "URL 结构应为 /uploads/yyyy/MM/dd/{uuid}.jpg: " + url);

        Path saved = physicalPathOf(url);
        assertTrue(Files.exists(saved), "文件应真的落盘: " + saved);
        assertArrayEquals(content, Files.readAllBytes(saved), "落盘内容应与上传内容一致");
        System.out.println("  落盘路径 : " + saved);
        System.out.println("  ✅ 落盘成功，内容一致");

        // 顺带验证「不信任 Content-Type」：同一个文件，Content-Type 伪造成
        // application/x-msdownload（可执行文件）也照样按 .jpg 接收 ——
        // 说明放行与否只看扩展名白名单，不看这个可以随便写的头。
        MockMultipartFile forgedType = new MockMultipartFile(
                "file", "evil.jpg", "application/x-msdownload", content);
        String url2 = fileService.upload(forgedType);
        assertTrue(Files.exists(physicalPathOf(url2)));
        System.out.println("  ✅ 伪造 Content-Type 仍按扩展名处理，仅记录日志: " + url2);
        System.out.println("  清理: " + saved.getParent());
    }

    // ================================================================
    // ② 空文件
    // ================================================================
    @Test
    @DisplayName("② 空文件 → 抛 BizException")
    void emptyFile() {
        System.out.println();
        System.out.println("========== ② 空文件 ==========");

        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);

        BizException e = assertThrows(BizException.class, () -> fileService.upload(empty));
        System.out.println("  ✅ 抛 BizException: " + e.getMessage());
        assertEquals(400, e.getCode(), "业务异常默认 code 应为 400");

        // null 也要挡住（跳过上传时 Service 可能被直接调用）
        assertThrows(BizException.class, () -> fileService.upload(null));
        System.out.println("  ✅ null 也抛 BizException");
    }

    // ================================================================
    // ③ 超大文件
    // ================================================================
    @Test
    @DisplayName("③ 超大文件（>5MB）→ 抛 BizException")
    void oversizeFile() throws IOException {
        System.out.println();
        System.out.println("========== ③ 超大文件 >5MB ==========");

        // 5MB + 1 字节：刚好越界，用来验证边界是「> 5MB 拒绝」而不是「>= 5MB 拒绝」
        byte[] tooBig = new byte[(int) MAX_SIZE + 1];
        MockMultipartFile big = new MockMultipartFile(
                "file", "big.jpg", "image/jpeg", tooBig);
        System.out.println("  构造文件大小: " + big.getSize() + " 字节（上限 " + MAX_SIZE + "）");

        BizException e = assertThrows(BizException.class, () -> fileService.upload(big));
        System.out.println("  ✅ 超 1 字节就抛 BizException: " + e.getMessage());

        // 边界另一侧：正好 5MB 应该放行（证明判断是 > 而不是 >=）
        byte[] exact = new byte[(int) MAX_SIZE];
        MockMultipartFile exactFile = new MockMultipartFile(
                "file", "exact.jpg", "image/jpeg", exact);
        String url = fileService.upload(exactFile);
        assertTrue(Files.exists(physicalPathOf(url)), "正好 5MB 的文件应被接收");
        System.out.println("  ✅ 正好 5MB 放行: " + url);
    }

    // ================================================================
    // ④ 非法扩展名
    // ================================================================
    @Test
    @DisplayName("④ 非法扩展名（.exe/.sh/.svg）→ 抛 BizException")
    void illegalExtension() {
        System.out.println();
        System.out.println("========== ④ 非法扩展名 ==========");

        byte[] content = fakeImageBytes();

        // .exe 和 .sh：典型的可执行/脚本，落到服务器上就是 WebShell 的原料
        String[] badNames = {"evil.exe", "script.sh", "shell.jsp", "a.jpg.exe", "x.SVG"};
        for (String name : badNames) {
            MockMultipartFile bad = new MockMultipartFile("file", name, "image/jpeg", content);
            BizException e = assertThrows(BizException.class, () -> fileService.upload(bad),
                    "扩展名 " + name + " 应被拒绝");
            assertEquals(400, e.getCode());
            System.out.println("  ✅ " + name + " 被拒绝: " + e.getMessage());
        }

        // 双扩展名要特别说明：a.jpg.exe 取的是最后一个点之后的 exe，
        // 所以被拒 —— 如果实现是「找第一个点」就会误判成 jpg 放行。
        System.out.println("  ✅ 双扩展名 a.jpg.exe 取最后一段 exe，已拒绝");
    }

    // ================================================================
    // ⑤ 路径穿越攻击（重点用例）
    // ================================================================
    @Test
    @DisplayName("⑤ 路径穿越：../../evil.jpg → 最终文件名必须是 UUID，路径不含 ..")
    void pathTraversal() {
        System.out.println();
        System.out.println("========== ⑤ 路径穿越攻击 ==========");

        byte[] content = fakeImageBytes();

        // 两种分隔符都要试：
        //   Linux 服务端只认 /，Windows 两个都认 —— 本项目跑在 Windows 上，
        //   只处理 / 的实现会被 ..\..\ 绕过。
        String[] attacks = {"../../evil.jpg", "..\\..\\evil.jpg", "../../../../tmp/hack.jpg"};

        for (String attack : attacks) {
            MockMultipartFile file = new MockMultipartFile("file", attack, "image/jpeg", content);

            String url = fileService.upload(file);
            System.out.println("  攻击文件名: " + attack);
            System.out.println("  实际返回  : " + url);

            // 断言 1：URL 里不能出现 ..
            assertFalse(url.contains(".."), "返回的 URL 里不能含 ..: " + url);

            // 断言 2：文件名必须是 32 位 UUID + .jpg，原始文件名一个字符都不留
            String fileName = url.substring(url.lastIndexOf('/') + 1);
            assertTrue(fileName.matches("[0-9a-f]{32}\\.jpg"),
                    "文件名必须是 UUID.jpg，实际是: " + fileName);

            // 断言 3：物理路径必须仍在上传根目录之下（没被 .. 带出去）
            Path saved = physicalPathOf(url).normalize();
            Path root = fileService.getRootDir().normalize();
            assertTrue(saved.startsWith(root),
                    "文件必须落在上传根目录内: " + saved + " 根=" + root);
            assertTrue(Files.exists(saved), "文件应真的落盘: " + saved);

            System.out.println("  落盘路径  : " + saved + "  ✅ 仍在 rootDir 内");
        }

        System.out.println("  ✅ 三种穿越写法都被 UUID 重命名化解");
    }

    // ================================================================
    // ⑥ 无扩展名
    // ================================================================
    @Test
    @DisplayName("⑥ 无扩展名 → 抛 BizException")
    void noExtension() {
        System.out.println();
        System.out.println("========== ⑥ 无扩展名 ==========");

        byte[] content = fakeImageBytes();

        // 没有任何点
        assertThrows(BizException.class, () -> fileService.upload(
                new MockMultipartFile("file", "noext", "image/jpeg", content)));
        System.out.println("  ✅ noext 被拒绝");

        // 点是最后一个字符：file. 的扩展名是空串，同样要拒
        assertThrows(BizException.class, () -> fileService.upload(
                new MockMultipartFile("file", "noext.", "image/jpeg", content)));
        System.out.println("  ✅ noext. 被拒绝");

        // 原文件名为 null（某些客户端确实不传 filename 字段）
        assertThrows(BizException.class, () -> fileService.upload(
                new MockMultipartFile("file", null, "image/jpeg", content)));
        System.out.println("  ✅ 文件名为 null 被拒绝");
    }
}
