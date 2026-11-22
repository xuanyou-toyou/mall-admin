package demo.mall.service;

import demo.mall.common.BizException;
import demo.mall.common.ResultCode;
import demo.mall.dao.UserDao;
import demo.mall.dto.LoginRequest;
import demo.mall.dto.LoginResponse;
import demo.mall.dto.RegisterRequest;
import demo.mall.model.User;
import demo.mall.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户业务层。
 *
 * 这一层负责：
 *   - 业务规则校验（用户名唯一、手机号唯一）
 *   - 密码加密
 *   - 组合多次 DAO 调用
 *
 * 注意：Bean Validation 注解（@NotBlank 那些）在 Controller 层就拦掉了，
 *      走到这里的时候，username / password / phone 已经保证非空且格式正确。
 *      所以这里只需要校验「业务唯一性」，不需要重复做格式校验。
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserService(UserDao userDao, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userDao = userDao;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 注册新用户。
     *
     * ============================================================
     * 你要实现的内容
     * ============================================================
     *
     * 【校验顺序 —— 从便宜到贵】
     *
     *   ① 用户名是否已被占用
     *        userDao.findByUsername(req.getUsername()) != null
     *        → 抛 BizException("用户名已被注册")
     *
     *   ② 手机号是否已被占用
     *        注意：现在的 UserDao 只有 findByUsername，
     *        你需要先给 UserDao 加一个 findByPhone(String phone) 方法
     *        → 抛 BizException("手机号已被注册")
     *
     *   ③ 密码确认（如果有「确认密码」字段的话，我们没有，跳过）
     *
     * 【然后落库】
     *
     *   ④ 把 RegisterRequest 转成 User
     *        username ← req.getUsername()
     *        password ← passwordEncoder.encode(req.getPassword())   ← 关键：加密！
     *        nickname ← req.getNickname()
     *        phone    ← req.getPhone()
     *
     *   ⑤ userDao.insert(user)
     *        返回 false 说明插入失败 → 抛 BizException("注册失败，请稍后重试")
     *
     *   ⑥ 记一条日志
     *        log.info("用户注册成功: username={}, id={}", ...)
     *
     * ============================================================
     * 三个必须想清楚的点
     * ============================================================
     *
     * 【点 1】密码什么时候加密？
     *   必须在「存库之前」加密，而且只能是 BCrypt 的 encode()。
     *   如果先存明文再加密，中间有一瞬间数据库里是明文 —— 这是事故。
     *
     * 【点 2】为什么不用 @Transactional？
     *   注册只写一张表（user），单条 INSERT 本身就是原子的，不需要事务。
     *   事务是在「多个写操作必须一起成功/失败」时才需要的（比如 W10 的批量导入）。
     *   ——能说清「什么时候不需要事务」，比会用事务更能体现理解。
     *
     * 【点 3】唯一性校验和数据库的 UNIQUE 约束，会不会重复？
     *   会重复，但两层都要有：
     *     业务层校验 → 给用户友好提示「用户名已被注册」
     *     数据库 UNIQUE → 兜底，防止并发下两个请求同时通过业务校验
     *   如果只依赖业务层校验，并发场景下会插入两条同名记录。
     *
     * @param req 注册请求（字段已通过 Bean Validation 校验）
     */
    public void register(RegisterRequest req) {
        if(userDao.findByUsername(req.getUsername()) != null) {
            throw new BizException("用户名已被注册");
        }
        if(userDao.findByPhone(req.getPhone()) != null) {
            throw new BizException("手机号已被注册");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setNickname(req.getNickname());
        user.setPhone(req.getPhone());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        if (!userDao.insert(user)) {
            // ============================================================
            // 为什么这里是 500，不是默认的 400
            // ============================================================
            // 走到这一行说明：唯一性校验都过了，SQL 也执行了，但影响行数是 0。
            // 这不是「用户填错了什么」（那种情况上面两步已经拦掉了），
            // 而是服务器侧出了问题（表被锁、触发器拦截、连接异常被吞等）。
            //
            // 报 400 的两个真实危害（和 FileService 里 P0-1 同一个道理）：
            //   ① 前端会让用户改用户名/手机号重试，怎么改都不可能成功；
            //   ② 监控按状态码分类，4xx 不计入故障，注册全挂也不会告警。
            //
            // code 取 ResultCode.INTERNAL_ERROR(500)，GlobalExceptionHandler
            // 的 toHttpStatus 会把它映射成 HTTP 500；文案保持原文案不变，
            // 不把 SQL、表名、约束名这类内部信息泄漏给客户端（那些只进日志）。
            log.error("用户注册写库失败（影响行数为 0）：username={}", user.getUsername());
            throw new BizException(ResultCode.INTERNAL_ERROR.getCode(), "注册失败，请稍后重试");
        }
        log.info("用户注册成功：username={}, id={}", user.getUsername(), user.getId());

    }

    public LoginResponse login(LoginRequest req) {

        User user = userDao.findByUsername(req.getUsername());
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }


        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }


        if (user.getStatus() == 0) {
            throw new BizException("账号已被禁用");
        }


        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new LoginResponse(token, "Bearer", 7200000L,
                user.getId(), user.getUsername(), user.getNickname());
    }
}