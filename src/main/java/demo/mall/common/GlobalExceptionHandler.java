package demo.mall.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBizException(BizException e) {
        log.warn("业务异常，code={}，message={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(toHttpStatus(e.getCode()))
                .body(Result.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + "：" + error.getDefaultMessage())
                .collect(Collectors.joining("；"));
        log.warn("参数校验异常：{}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(ResultCode.BAD_REQUEST.getCode(), message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("参数异常：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(ResultCode.BAD_REQUEST.getCode(), e.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("资源不存在：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.fail(ResultCode.NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.fail(ResultCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e) {
        log.warn("缺少请求参数：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.fail(ResultCode.BAD_REQUEST));
    }

    /**
     * 路径变量 / 请求参数类型转换失败，比如 Long 类型收到了 "upload"。
     *
     * ============================================================
     * 这个异常是怎么来的（一条真实的踩坑路径）
     * ============================================================
     *
     *   GET /api/products/upload      ← 用户手滑，或者前端把 POST 写成了 GET
     *        ↓
     *   没有精确匹配 "GET /api/products/upload" 的映射
     *        ↓
     *   但 @GetMapping("/{id}") 能匹配上，于是 "upload" 被当成 id
     *        ↓
     *   "upload" 转 Long 失败 → MethodArgumentTypeMismatchException
     *
     * ============================================================
     * 为什么必须单独处理（不处理就是 500）
     * ============================================================
     * 不写这条 handler，它会落到最后的 @ExceptionHandler(Exception.class)，
     * 返回 500「服务器内部错误」。
     * 但请求方只是「参数传错了类型」——这是客户端错误，必须 400。
     * 报 500 会把客户端错误算到服务端头上：监控告警里全是 5xx，
     * 真正要处理的磁盘/DB 故障反而被淹没了。
     *
     * ============================================================
     * 为什么文案要带上参数名和实际值
     * ============================================================
     * 只说「参数错误」，前端根本不知道该改哪个参数。
     * 带上 "id" 和 "upload"，一眼就能看出「这个路径不是查详情的，
     * 你是不是把 /upload 当成 id 了」。
     *
     * 注意：只回显「参数名 + 用户自己传的值」，不回显异常栈、类名、
     * 期望的类型转换器 —— 那些属于内部实现细节，只写日志。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e) {
        // getName()  → 参数名，如 "id"
        // getValue() → 用户实际传的原始值，如 "upload"（可能为 null）
        String message = "参数 '" + e.getName() + "' 的值 '" + e.getValue() + "' 格式不正确";
        log.warn("参数类型不匹配：{}（目标类型 {}）", message, e.getRequiredType());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(ResultCode.BAD_REQUEST.getCode(), message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("请求体不可读：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.fail(ResultCode.BAD_REQUEST));
    }

    /**
     * 上传文件超过大小限制。
     *
     * ============================================================
     * 为什么必须单独写这一条（不写会返回 500，不是 400）
     * ============================================================
     *
     * spring.servlet.multipart.max-file-size=5MB 的限制不是在 Controller 里生效的，
     * 而是 Servlet 容器在「解析 multipart 请求体」时就抛出来了：
     *
     *   请求进来
     *     ↓ DispatcherServlet.doDispatch
     *     ↓ checkMultipart(request)          ← 这里就把请求体解析成 Part
     *     ↓ Tomcat 发现单个 Part 超过 maxFileSize，抛 SizeLimitExceededException
     *     ↓ Spring 包成 MaxUploadSizeExceededException
     *   此时 handler 还没解析出来，Controller 方法一行都没执行
     *
     * 也就是说：这个异常在「进 Controller 之前」就抛了，
     * FileService 里那层 5MB 判断根本没机会跑。
     *
     * 不写这条 handler 会怎样？
     *   它会被下面的 @ExceptionHandler(Exception.class) 兜底接住，
     *   返回 500「服务器内部错误」—— 但这对客户端是错误结论：
     *   是「你传的文件太大」（客户端问题，400），不是「服务器崩了」（500）。
     *   前端拿到 500 只会提示「系统异常」，用户不知道要换小图。
     *
     * ============================================================
     * 为什么这里返回 400 而不是 413
     * ============================================================
     * HTTP 语义上 413 Payload Too Large 更精确，但本项目所有业务失败
     * 都走 {code, message, data} 这套结构，且前端按 code 分支处理。
     * 返回 413 会让前端多一条特殊分支，收益不大 —— 保持和其它的 400 一致。
     *
     * 注意：这里是「兜底说明」，不是「唯一防线」。
     *   FileService 里仍然自己判一次 5MB，两者职责不同：
     *     这里 —— HTTP 层，超大请求在解析阶段就中断，省内存
     *     那里 —— 代码层，非 HTTP 调用（内部调用、测试）也受保护
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        // 原始异常里带着「到底超了哪个限制」，对排查有用，只进日志不返回给客户端
        log.warn("上传文件超过大小限制：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(ResultCode.BAD_REQUEST.getCode(), "上传文件超过大小限制（单文件最大 5MB）"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("服务器内部异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail(ResultCode.INTERNAL_ERROR));
    }

    private HttpStatus toHttpStatus(int code) {
        return switch (code) {
            case 200 -> HttpStatus.OK;
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 405 -> HttpStatus.METHOD_NOT_ALLOWED;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

}
