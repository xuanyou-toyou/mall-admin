package demo.mall.service;

import demo.mall.common.BizException;
import demo.mall.common.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 商品图片上传服务。
 *
 * ============================================================
 * 它在干什么
 * ============================================================
 *
 * 收到一个 MultipartFile，做完安全检查后落盘，返回一个可以直接访问的相对 URL：
 *
 *   上传 photo.JPG
 *        ↓  UUID 重命名 + 按日期分目录
 *   落盘  <mall.upload.dir>/2026/11/21/9f2c...e1.jpg
 *        ↓
 *   返回  /uploads/2026/11/21/9f2c...e1.jpg
 *
 * 这个 URL 拼到商品上（product.image_url），前端 <img src> 直接能显示，
 * 由 WebConfig 里的静态资源映射负责把 /uploads/** 指向磁盘目录。
 *
 * ============================================================
 * 为什么上传是最危险的一类接口
 * ============================================================
 *
 * 上传接口是少数「用户能把字节写进服务器磁盘」的入口。
 * 服务器上落一个攻击者可控的文件 = 一个 WebShell 潜在入口，
 * 所以下面每一条检查都不是形式主义，理由都写在对应位置。
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    /**
     * 扩展名白名单（全部小写）。
     *
     * 用「白名单」而不是「黑名单」：
     *   黑名单（禁止 exe、sh、jsp…）永远列不全 —— jspx、jspf、war、php、
     *   phtml、svg（能内嵌 script，可 XSS）、htaccess… 漏一个就是洞。
     *   白名单只放行「确定的图片扩展名」，其余一律拒绝，漏不了。
     *
     * 注意不含 svg：svg 是 XML，可以内嵌 <script>，
     * 当图片直接返回时能在浏览器里执行 JS，属于已知 XSS 载体，所以不收。
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /**
     * 单文件大小上限：5MB。
     *
     * application.properties 里的 spring.servlet.multipart.max-file-size=5MB 已经在
     * multipart 解析阶段挡了一层，这里为什么还要判一次？
     *   ① 配置可以被改（或者被 profile 覆盖），代码不该把安全完全外包给配置；
     *   ② 这个 Service 不是只能从 HTTP 进来 —— 将来若有定时任务、
     *      MQ 消费者、内部调用直接 new 一个 MockMultipartFile 进来，
     *      根本不经过 multipart 解析，配置那层形同虚设；
     *   ③ 单测可以直接调 Service，配置不会生效，只有代码判断才测得出来。
     */
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    /**
     * URL 前缀 —— 全项目唯一的事实来源。
     *
     * 这个字符串同时决定两件事，两者必须完全一致：
     *   ① 上传成功后返回的 URL 长什么样（这里拼）
     *   ② WebConfig 里静态资源映射的模式是什么（调 getUrlPrefix() 拼）
     *
     * 为什么不放在 WebConfig（或两边各写一遍）：
     *   以前 WebConfig 里硬编码 "/uploads/**"，这里硬编码 "/uploads"，
     *   两处独立字面量。如果只改了其中一处，会出现最难查的故障形态：
     *   接口返回 200 和一个看着完全正常的 URL，但 GET 它 404。
     *   更糟的是单元测试测不出来 —— 测试用 getRootDir() 反推磁盘路径，
     *   根本不过 HTTP 层，所以「URL 前缀」和「映射模式」对不上也能全绿。
     *   现在 WebConfig 只能从这里取，物理上不可能不一致。
     */
    private static final String URL_PREFIX = "/uploads";

    /** 按天分目录：yyyy/MM/dd。单目录文件过多会拖慢文件系统查找。 */
    private static final DateTimeFormatter DATE_DIR_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 上传根目录的绝对路径，构造时算好，避免每次上传都做路径解析。 */
    private final Path rootDir;

    /**
     * @param uploadDir 配置项 mall.upload.dir，默认 uploads（相对项目根目录）
     */
    public FileService(@Value("${mall.upload.dir:uploads}") String uploadDir) {
        // toAbsolutePath()：相对路径按「进程工作目录」解析成绝对路径。
        //   所以 mvn spring-boot:run / mvn test 跑出来都是 <项目根>/uploads。
        // normalize()：把 a/./b、a/../b 这类写法拍平，
        //   防止配置里写成 uploads/../uploads 之后，后面所有 startsWith 判断失真。
        this.rootDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        log.info("图片上传根目录: {}", this.rootDir);
    }

    /** 给 WebConfig 复用：静态资源映射必须指向同一个目录，否则 URL 能生成但访问 404。 */
    public Path getRootDir() {
        return rootDir;
    }

    /**
     * URL 前缀，供 WebConfig 拼静态资源映射模式（{@code getUrlPrefix() + "/**"}）。
     *
     * 由 Service 提供、Config 消费，是为了让「返回的 URL」和「能访问的路径」
     * 共用同一个常量。WebConfig 不再自己写 "/uploads" 字面量。
     *
     * @return 形如 /uploads，不带结尾斜杠
     */
    public String getUrlPrefix() {
        return URL_PREFIX;
    }

    /**
     * 校验并保存上传的图片。
     *
     * @param file 前端 multipart/form-data 里的 file 部分
     * @return 可访问的相对 URL，例如 /uploads/2026/11/21/9f2c...e1.jpg
     * @throws BizException 校验不通过（400）或落盘失败（500），
     *                      由 GlobalExceptionHandler 按 code 映射成对应的 HTTP 状态码
     */
    public String upload(MultipartFile file) {

        // ============================================================
        // 检查 ①：文件不能为空
        // ============================================================
        // 为什么要判：
        //   前端没选文件、或者 form 里字段名写错（不是 file），
        //   @RequestParam 拿到的可能是一个 size=0 的空文件而不是 null。
        //   不判的话会落一个 0 字节的「图片」到磁盘，列表页一片破图，
        //   而且白占一个 UUID 文件名，排查半天才发现是空文件。
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }

        // ============================================================
        // 检查 ②：大小 ≤ 5MB（配置挡过一层，代码再挡一层，理由见 MAX_FILE_SIZE 注释）
        // ============================================================
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException("文件大小不能超过 5MB");
        }

        // ============================================================
        // 检查 ④：扩展名白名单（从「原文件名」取扩展名，转小写后校验）
        // ============================================================
        // 为什么取原文件名：Content-Type 可以随便伪造，扩展名至少是用户主动声明的，
        //   两者都不完全可信 —— 所以真正的防线是「白名单 + 重命名 + 不当脚本执行」，
        //   而不是这两个字段里任何一个。
        String originalFilename = file.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BizException("只允许上传 jpg / jpeg / png / gif / webp 格式的图片");
        }

        // ============================================================
        // 检查 ⑤：不信任 Content-Type，只把它记进日志
        // ============================================================
        // 为什么不信：Content-Type 是请求方自己填的字符串。
        //   把 evil.exe 改名成 evil.jpg、再把 Content-Type 写成 image/jpeg，
        //   curl 一行就能做到 —— 所以它无法用来判断「这真的是图片」。
        // 为什么还记录：它不影响放行与否，但出问题时（比如有人传了怪异文件）
        //   日志里能同时看到 originalFilename 和 contentType 的对应关系，便于溯源。
        log.info("收到图片上传: originalFilename={}, contentType={}, size={}B",
                originalFilename, file.getContentType(), file.getSize());

        // ============================================================
        // 检查 ③：文件名必须重新生成（UUID），绝不能用用户传来的文件名
        // ============================================================
        // 直接使用 originalFilename 会有两个致命问题：
        //   1. 路径穿越：文件名传 ../../../../etc/passwd 或 ..\..\webapps\shell.jsp，
        //      拼出来的路径就跑到目标目录外面去了，写到哪完全由攻击者决定；
        //   2. 覆盖：传一个和已有文件同名的名字，就能覆盖掉别人的图片。
        // UUID 重命名一次性解决两个问题：
        //   - 新名字里只有 [0-9a-f]{32} + 白名单扩展名，不含 /、\、.、.. 任何危险字符；
        //   - 32 位十六进制随机串，碰撞概率忽略不计，不可能覆盖已有文件。
        // 代价是丢掉原始文件名 —— 所以原始名只留在上面那行日志里。
        String newFileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;

        // 按日期分目录：2026/11/21
        String datePath = LocalDate.now().format(DATE_DIR_FORMATTER);

        // 最终目标目录 / 目标文件
        Path targetDir = rootDir.resolve(datePath).normalize();
        Path targetFile = targetDir.resolve(newFileName).normalize();

        // 纵深防御：即使上面全都没问题，落盘前再确认一次
        // 「最终路径必须仍在 rootDir 之下」，不满足就直接拒绝。
        // 这一条是给未来的人看的保险 —— 万一哪天有人改动了上面拼路径的逻辑
        // （比如又去用 originalFilename），这里会立刻拦下而不是悄悄写到目录外。
        if (!targetDir.startsWith(rootDir) || !targetFile.startsWith(rootDir)) {
            throw new BizException("非法的上传路径");
        }

        // 落盘：用 getInputStream() 直接流式拷贝，
        //   不把整个文件读进内存 byte[]（大文件会撑爆堆）。
        // 用 StandardCopyOption.REPLACE_EXISTING：UUID 重名概率可忽略，
        //   真撞上了覆盖自己也不算数据丢失。
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(targetDir);
            Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // 磁盘满、没权限、目录被删、文件句柄耗尽…… 都归到这里。
            //
            // ============================================================
            // 为什么这里必须是 500，不能是默认的 400
            // ============================================================
            // 这些全是「服务器侧故障」，不是客户端做错了什么。
            // 如果报 400，会产生两个真实危害：
            //   ① 前端会把它当成「参数不对」—— 提示用户改文件名、换图片、
            //      改格式，用户怎么改都不可能成功，白白来回试；
            //   ② 监控/告警通常按状态码分类，4xx 记为客户端错误不计入故障，
            //      磁盘写满这种要命的故障就被静默吞掉了。
            // 用 5xx 才能让「重试」「换机器」「叫运维」这些动作做对。
            //
            // 具体 code 取 ResultCode.INTERNAL_ERROR(500)，
            // GlobalExceptionHandler.toHttpStatus 的 default 分支会把它映射成
            // HTTP 500，响应体仍是统一的 {code,message,data}。
            //
            // 文案保持原样、不带任何内部信息：
            //   targetFile 这类服务器绝对路径、异常类名、栈信息绝不能进响应体
            //   （那等于把目录结构告诉攻击者）；这些只写进日志。
            log.error("保存上传文件失败: target={}", targetFile, e);
            throw new BizException(ResultCode.INTERNAL_ERROR.getCode(), "文件保存失败，请稍后重试");
        }

        String url = URL_PREFIX + "/" + datePath + "/" + newFileName;
        log.info("图片上传成功: url={}, size={}B", url, file.getSize());
        return url;
    }

    /**
     * 从原始文件名里取扩展名，取不到返回 null。
     *
     * 细节都在这里，一条条说：
     *
     * ① 先砍掉路径部分，只留最后一段。
     *    浏览器一般只传裸文件名，但 multipart 规范允许带路径，
     *    curl / 脚本/ 老 IE 传 ../../evil.jpg 是可能的。
     *    用 substring 只留最后一段，后面的扩展名判断才不会被路径里的点干扰。
     *    为什么同时处理 / 和 \：Linux 只认 /，Windows 两个都认，
     *    这个项目跑在 Windows 上，只处理 / 会被 ..\..\evil.jpg 绕过。
     *
     * ② 找最后一个点。取「最后一个」而不是「第一个」：
     *    a.tar.gz 的扩展名是 .gz 不是 .tar。
     *
     * ③ 没有点、或者点就是最后一个字符（file.）→ 视为没有扩展名，返回 null。
     *    注意 null 不会被当成「通过」——调用方判的是 null/白名单不命中就拒绝。
     *
     * ④ toLowerCase(Locale.ROOT)：统一转小写，photo.JPG 也能过白名单。
     *    用 Locale.ROOT 而不是默认 Locale：土耳其语环境下
     *    "I".toLowerCase() 会变成无点的 ı，导致 JPG 这种扩展名判断异常。
     */
    private String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return null;
        }
        int lastSlash = Math.max(originalFilename.lastIndexOf('/'), originalFilename.lastIndexOf('\\'));
        String nameOnly = (lastSlash >= 0) ? originalFilename.substring(lastSlash + 1) : originalFilename;

        int dot = nameOnly.lastIndexOf('.');
        if (dot < 0 || dot == nameOnly.length() - 1) {
            return null;
        }
        return nameOnly.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
