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
-- 商品表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(100)  NOT NULL                COMMENT '商品名',
    `sell`        DECIMAL(10,2) NOT NULL                COMMENT '售价',
    `stock`       INT           NOT NULL DEFAULT 0      COMMENT '库存',
    `image_url`   VARCHAR(255)  DEFAULT NULL            COMMENT '主图地址',
    `category_id` BIGINT        NOT NULL                COMMENT '分类 id（分类表 W12 再做）',
    `status`      TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：0=下架 1=上架',
    `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品表';
