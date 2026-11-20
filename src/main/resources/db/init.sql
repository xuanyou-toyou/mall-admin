-- ============================================================
-- mall-admin 数据库初始化脚本
--
-- 用法：
--   mysql -u root -p < init.sql
--   或在 DataGrip / Navicat 里整段执行
--
-- 说明：
--   category 表在 W12 实现，product.category_id 暂时不加外键约束，
--   避免表结构未定就锁死。
-- ============================================================

CREATE DATABASE IF NOT EXISTS mall_db
    DEFAULT CHARSET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE mall_db;

-- ------------------------------------------------------------
-- 用户表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`   VARCHAR(50)  NOT NULL                COMMENT '用户名，登录用',
    `password`   VARCHAR(60)  NOT NULL                COMMENT 'BCrypt 加密后的密码，固定 60 字符',
    `nickname`   VARCHAR(100) DEFAULT NULL            COMMENT '昵称，可不填',
    `phone`      VARCHAR(20)  NOT NULL                COMMENT '手机号',
    `status`     TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0=禁用 1=正常',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- ------------------------------------------------------------
-- 商品分类表
--
-- 用 parent_id 表示层级：
--   parent_id = 0  → 顶级分类（如「电子产品」）
--   parent_id = 5  → 挂在 id=5 的分类下（如「手机」挂在「电子产品」下）
--
-- 为什么不加外键约束？
--   1. parent_id = 0 不是合法的外键值，数据库无法约束「顶级分类用 0」
--   2. 外键会带来写入时的锁开销，业务层校验更灵活
--   3. 绝大多数互联网项目都不加外键，靠业务层保证
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_id`  BIGINT      NOT NULL DEFAULT 0      COMMENT '父分类 id，0 表示顶级分类',
    `name`       VARCHAR(50) NOT NULL                COMMENT '分类名',
    `sort`       INT         NOT NULL DEFAULT 0      COMMENT '排序值，数字小的排前面',
    `status`     TINYINT     NOT NULL DEFAULT 1      COMMENT '状态：0=停用 1=正常',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 联合唯一：同一个父分类下不能有重名
    -- 但不同父分类下可以重名（电子产品/配件、服装/配件 都允许）
    UNIQUE KEY `uk_parent_name` (`parent_id`, `name`),
    -- 查「某分类下有哪些子分类」会用到
    KEY `idx_parent` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品分类表';

-- ------------------------------------------------------------
-- 商品表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(100)  NOT NULL                COMMENT '商品名',
    `sell`        DECIMAL(10,2) NOT NULL                COMMENT '售价',
    `stock`       INT           NOT NULL DEFAULT 0      COMMENT '库存',
    `image_url`   VARCHAR(255)  DEFAULT NULL            COMMENT '主图地址',
    `category_id` BIGINT        NOT NULL                COMMENT '分类 id，指向 category 表',
    `status`      TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：0=下架 1=上架',
    `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品表';
