# mall-admin

商城后台管理系统骨架，包含统一的接口响应和异常处理。MyBatis-Plus 与 Spring Boot 4 的兼容性验证未通过，当前按任务约束使用 Spring JDBC。

## 技术栈

- Java 21
- Spring Boot 4.1.2（Spring Framework 7）
- Spring JDBC
- MySQL
- Maven

## 运行方式

1. 参考 `src/main/resources/application-local.properties.example` 配置 `src/main/resources/application-local.properties`。该真实配置文件已加入 `.gitignore`，不要提交数据库密码。
2. 执行 `mvn compile` 编译项目，执行 `mvn test` 运行测试。
3. 执行 `mvn spring-boot:run` 启动应用。
4. 访问 `curl http://localhost:8080/api/ping`，正常返回统一响应体，数据为 `pong`。

当前骨架通过 `spring.datasource.hikari.initialization-fail-timeout=-1` 将框架启动与数据库可用性分开；实际数据库业务仍需要可用的 MySQL 和 `mall_db` 数据库。

## 当前进度

- 已建立 `demo.mall` 包结构。
- 已验证 MyBatis-Plus 3.5.18.1、mybatis-spring 3.0.6 与 Spring Boot 4.1.2（Spring Framework 7.0.10）的组合；自动配置因 DataSource 排序问题未生效，已回退到 Spring JDBC。
- 已实现统一响应体、响应码、业务异常和全局异常处理。
- 已提供 `/api/ping` 验证接口。
- 未实现用户注册、登录、JWT 等业务功能。
