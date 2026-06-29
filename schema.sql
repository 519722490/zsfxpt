-- MySQL 8.0 建表脚本：用于初始化本项目的业务数据库结构。
-- 说明：本脚本使用 utf8mb4 字符集，方便保存中文、表情和其他完整 Unicode 字符。
-- 说明：所有表都使用 InnoDB 引擎，支持事务、行级锁和外键约束。
-- ============================================================
-- users 表：保存平台用户的基础资料、登录标识和个人资料信息。
CREATE TABLE IF NOT EXISTS users ( -- 如果 users 表不存在就创建，避免重复执行脚本时报错。
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户主键ID；无符号大整数；由数据库自动递增生成', -- 每个用户的唯一编号，是其他表关联用户时最常用的字段。
    phone VARCHAR(32) NULL COMMENT '手机号；允许为空；用于手机号登录或绑定手机号', -- 设置唯一索引后，同一个手机号只能绑定一个用户；NULL 可以有多条。
    email VARCHAR(128) NULL COMMENT '邮箱地址；允许为空；用于邮箱登录或绑定邮箱', -- 设置唯一索引后，同一个邮箱只能绑定一个用户；NULL 可以有多条。
    password_hash VARCHAR(128) NULL COMMENT '密码哈希值；只保存加密后的密码，不保存明文密码', -- 登录校验时拿用户输入密码加密后与这里对比。
    nickname VARCHAR(64) NOT NULL COMMENT '用户昵称；不能为空；用于页面展示', -- 用户对外展示的名字，注册或创建资料时必须提供。
    avatar TEXT NULL COMMENT '头像地址；允许为空；通常保存图片URL或对象存储地址', -- TEXT 适合保存较长的头像链接。
    bio VARCHAR(512) NULL COMMENT '个人简介；允许为空；最多512个字符', -- 用于展示用户的自我介绍。
    zg_id VARCHAR(64) NULL COMMENT '平台内唯一账号ID；允许为空；可作为用户对外展示或搜索的唯一标识', -- 设置唯一索引后，同一个 zg_id 只能属于一个用户。
    gender VARCHAR(16) NULL COMMENT '性别；允许为空；可存 male、female、unknown 等业务约定值', -- 使用字符串便于后续扩展更多枚举值。
    birthday DATE NULL COMMENT '生日；允许为空；只保存日期，不保存具体时间', -- DATE 类型适合生日这种不需要时分秒的字段。
    school VARCHAR(128) NULL COMMENT '学校名称；允许为空；用于用户资料展示或校园场景筛选', -- 保存用户填写的学校文本。
    tags_json JSON NULL COMMENT '用户标签JSON；允许为空；用于保存兴趣标签、身份标签等数组结构', -- JSON 类型方便保存多个标签，例如 ["Java", "考研"]。
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间；插入数据时自动填入当前时间', -- 记录用户账号第一次创建的时间。
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；插入时自动填入，后续更新记录时自动刷新', -- 记录用户资料最后一次被修改的时间。
    PRIMARY KEY (id), -- 主键索引；保证 id 唯一，并加快通过 id 查询用户的速度。
    UNIQUE KEY uk_users_phone (phone), -- 手机号唯一索引；防止多个账号绑定同一个手机号。
    UNIQUE KEY uk_users_email (email), -- 邮箱唯一索引；防止多个账号绑定同一个邮箱。
    UNIQUE KEY uk_users_zg_id (zg_id) -- 平台账号ID唯一索引；防止多个账号使用同一个 zg_id。
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户基础信息表'; -- 指定表引擎、字符集、排序规则和表说明。
-- ============================================================
-- login_logs 表：保存用户登录记录，方便审计、安全排查和登录历史展示。
CREATE TABLE IF NOT EXISTS login_logs ( -- 如果 login_logs 表不存在就创建，用来记录每一次登录尝试。
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '登录日志主键ID；无符号大整数；由数据库自动递增生成', -- 每条登录记录的唯一编号。
    user_id BIGINT UNSIGNED NULL COMMENT '用户ID；允许为空；登录失败或账号不存在时可能没有对应用户', -- 成功识别用户时可关联 users.id。
    identifier VARCHAR(128) NOT NULL COMMENT '登录标识；不能为空；可以是手机号、邮箱或其他账号标识', -- 记录用户当时使用什么账号信息尝试登录。
    channel VARCHAR(32) NOT NULL COMMENT '登录渠道；不能为空；例如 password、sms、email、oauth 等', -- 用于区分密码登录、验证码登录、第三方登录等方式。
    ip VARCHAR(45) NULL COMMENT '登录IP地址；允许为空；兼容 IPv4 和 IPv6', -- IPv6 最长可到45个字符，因此这里使用 VARCHAR(45)。
    user_agent VARCHAR(512) NULL COMMENT '浏览器或客户端User-Agent；允许为空；用于识别设备和客户端信息', -- 可以辅助排查异常登录或统计客户端类型。
    status VARCHAR(16) NOT NULL COMMENT '登录状态；不能为空；例如 success、failed、locked 等', -- 记录这次登录尝试最终是否成功。
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '日志创建时间；插入数据时自动填入当前时间', -- 记录这次登录行为发生的时间。
    PRIMARY KEY (id), -- 主键索引；保证每条登录日志都有唯一ID。
    KEY ix_login_logs_user_created_at (user_id, created_at) -- 普通联合索引；加快按用户查询登录历史并按时间排序的速度。
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志表'; -- 指定表引擎、字符集、排序规则和表说明。
-- ============================================================
-- know_posts 表：保存“知文”内容主表数据，包括标题、摘要、正文地址、图片和发布状态。
CREATE TABLE IF NOT EXISTS know_posts ( -- 如果 know_posts 表不存在就创建，用来保存用户发布的内容。
    id BIGINT UNSIGNED NOT NULL COMMENT '知文主键ID；由业务层生成，例如雪花算法；不是数据库自增', -- 内容ID通常需要在分布式场景下提前生成。
    tag_id BIGINT UNSIGNED NULL COMMENT '主分类ID；允许为空；表示内容所属的一级分类或主标签', -- 用于按分类筛选内容。
    tags JSON NULL COMMENT '标签名称数组；允许为空；例如 ["java", "编程"]', -- JSON 数组适合保存多个普通标签。
    title VARCHAR(256) NULL COMMENT '标题；允许为空；最多256个字符', -- 用于列表页、详情页展示内容标题。
    description VARCHAR(50) NULL COMMENT '摘要或简短描述；允许为空；最多50个字符', -- 控制摘要长度，适合列表卡片展示。
    content_url TEXT NULL COMMENT '正文访问地址；允许为空；通常是OSS上的URL或签名URL', -- 正文内容不直接塞进数据库，只保存访问地址。
    content_object_key VARCHAR(512) NULL COMMENT '正文在OSS中的对象Key；允许为空；用于定位对象存储中的文件', -- 删除、刷新签名、重新读取文件时会用到。
    content_etag VARCHAR(128) NULL COMMENT '正文文件的OSS ETag；允许为空；用于校验文件是否变化', -- ETag 可用于缓存校验或文件一致性判断。
    content_size BIGINT UNSIGNED NULL COMMENT '正文字节大小；允许为空；无符号大整数', -- 记录文件大小，方便限制上传大小或展示文件信息。
    content_sha256 CHAR(64) NULL COMMENT '正文SHA-256哈希值；允许为空；固定64位十六进制字符串', -- 用于校验正文内容完整性或去重。
    creator_id BIGINT UNSIGNED NOT NULL COMMENT '作者用户ID；不能为空；关联 users.id', -- 标识这篇内容是谁创建的。
    is_top TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否置顶；0表示不置顶，1表示置顶', -- 用于列表排序或运营推荐。
    type VARCHAR(32) NOT NULL DEFAULT 'image_text' COMMENT '内容类型；默认 image_text，后续可扩展 video、article 等', -- 用字符串保存类型，便于新增内容形态。
    visible VARCHAR(32) NOT NULL DEFAULT 'public' COMMENT '可见范围；默认 public，可扩展 private、friends 等', -- 控制内容对哪些用户可见。
    img_urls JSON NULL COMMENT '图片URL数组或图片对象数组；允许为空', -- 一篇内容可以对应多张图片，因此使用 JSON 保存。
    video_url TEXT NULL COMMENT '视频URL；允许为空；一期可以不用，后续扩展视频内容时使用', -- 保留视频字段，避免后续改表成本过高。
    status VARCHAR(16) NOT NULL DEFAULT 'draft' COMMENT '内容状态；默认 draft，可使用 draft、reviewing、published、rejected、deleted 等', -- 用于草稿、审核、发布、删除等流程控制。
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间；插入数据时自动填入当前时间', -- 记录内容第一次创建的时间。
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；插入时自动填入，后续更新记录时自动刷新', -- 记录内容最后一次修改的时间。
    publish_time TIMESTAMP NULL DEFAULT NULL COMMENT '发布时间；允许为空；内容正式发布后再写入', -- 草稿或审核中内容通常没有发布时间。
    PRIMARY KEY (id), -- 主键索引；保证每篇内容ID唯一，并加快按ID查询详情的速度。
    KEY ix_know_posts_creator_ct (creator_id, create_time), -- 作者和创建时间索引；加快查询某个作者的内容列表。
    KEY ix_know_posts_status_ct (status, create_time), -- 状态和创建时间索引；加快按状态筛选内容并按创建时间排序。
    KEY ix_know_posts_tag_ct (tag_id, create_time), -- 分类和创建时间索引；加快按分类查询内容列表。
    KEY ix_know_posts_top_ct (is_top, create_time), -- 置顶和创建时间索引；加快查询置顶内容和普通内容排序。
    KEY ix_know_posts_creator_status_pub (creator_id, status, publish_time), -- 作者、状态和发布时间索引；加快查询某个作者已发布内容的速度。
    CONSTRAINT fk_know_posts_creator FOREIGN KEY (creator_id) REFERENCES users(id) -- 外键约束；保证 creator_id 必须指向 users 表中真实存在的用户。
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知文内容主表'; -- 指定表引擎、字符集、排序规则和表说明。
-- ============================================================
-- outbox 表：保存待异步处理或待投递的业务事件，常用于消息最终一致性。
CREATE TABLE IF NOT EXISTS outbox ( -- 如果 outbox 表不存在就创建，用来保存可靠事件消息。
    id BIGINT UNSIGNED NOT NULL COMMENT '事件主键ID；通常由业务层生成，例如雪花算法', -- 每条事件消息的唯一编号。
    aggregate_type VARCHAR(64) NOT NULL COMMENT '聚合类型；不能为空；表示事件属于哪类业务对象', -- 例如 user、know_post、follow 等。
    aggregate_id BIGINT UNSIGNED NULL COMMENT '聚合ID；允许为空；表示具体业务对象的ID', -- 例如某篇内容ID或某个用户ID。
    type VARCHAR(64) NOT NULL COMMENT '事件类型；不能为空；表示具体发生了什么事件', -- 例如 USER_CREATED、POST_PUBLISHED 等。
    payload JSON NOT NULL COMMENT '事件内容JSON；不能为空；保存事件投递需要的完整数据', -- 消费者可以从这里读取业务事件参数。
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件创建时间；精确到毫秒；插入时自动填入', -- 毫秒精度方便消息排序和排查。
    PRIMARY KEY (id), -- 主键索引；保证每条事件消息唯一。
    KEY ix_outbox_agg (aggregate_type, aggregate_id), -- 聚合类型和聚合ID索引；加快查询某个业务对象相关事件。
    KEY ix_outbox_ct (created_at) -- 创建时间索引；加快按时间扫描待处理事件。
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事务消息出站表'; -- 指定表引擎、字符集、排序规则和表说明。
-- ============================================================
-- following 表：保存“我关注了谁”的关系，查询方向以 from_user_id 为主。
CREATE TABLE IF NOT EXISTS following ( -- 如果 following 表不存在就创建，用来从关注者角度保存关注关系。
    id BIGINT UNSIGNED NOT NULL COMMENT '关注关系主键ID；通常由业务层生成，例如雪花算法', -- 每条关注关系的唯一编号。
    from_user_id BIGINT UNSIGNED NOT NULL COMMENT '发起关注的用户ID；不能为空', -- 表示“谁去关注别人”。
    to_user_id BIGINT UNSIGNED NOT NULL COMMENT '被关注的用户ID；不能为空', -- 表示“被谁关注”。
    rel_status TINYINT NOT NULL DEFAULT 1 COMMENT '关系状态；默认1；通常1表示有效关注，0表示取消或无效', -- 保留状态字段可以避免物理删除历史关系。
    created_at DATETIME(3) NOT NULL COMMENT '关系创建时间；精确到毫秒；由业务层写入', -- 记录第一次关注发生的时间。
    updated_at DATETIME(3) NOT NULL COMMENT '关系更新时间；精确到毫秒；由业务层写入', -- 记录关注关系状态最后一次变化时间。
    PRIMARY KEY (id), -- 主键索引；保证每条关注关系有唯一ID。
    UNIQUE KEY uk_from_to (from_user_id, to_user_id), -- 关注者和被关注者唯一索引；防止同一个用户重复关注同一个人。
    KEY idx_from_created (from_user_id, created_at, to_user_id, rel_status), -- 关注者维度列表索引；加快查询“我关注的人”并按时间排序。
    KEY idx_to (to_user_id, from_user_id, rel_status) -- 被关注者维度辅助索引；加快反向查询某人是否被某用户关注。
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关注关系表：我关注的人'; -- 指定表引擎、字符集、排序规则和表说明。
-- ============================================================
-- follower 表：保存“谁关注了我”的关系，查询方向以 to_user_id 为主。
CREATE TABLE IF NOT EXISTS follower ( -- 如果 follower 表不存在就创建，用来从粉丝角度保存关注关系。
    id BIGINT UNSIGNED NOT NULL COMMENT '粉丝关系主键ID；通常由业务层生成，例如雪花算法', -- 每条粉丝关系的唯一编号。
    to_user_id BIGINT UNSIGNED NOT NULL COMMENT '被关注的用户ID；不能为空', -- 表示“这个人拥有粉丝”。
    from_user_id BIGINT UNSIGNED NOT NULL COMMENT '发起关注的用户ID；不能为空', -- 表示“这个粉丝是谁”。
    rel_status TINYINT NOT NULL DEFAULT 1 COMMENT '关系状态；默认1；通常1表示有效关注，0表示取消或无效', -- 和 following 表保持相同状态语义。
    created_at DATETIME(3) NOT NULL COMMENT '关系创建时间；精确到毫秒；由业务层写入', -- 记录粉丝关系第一次建立的时间。
    updated_at DATETIME(3) NOT NULL COMMENT '关系更新时间；精确到毫秒；由业务层写入', -- 记录粉丝关系状态最后一次变化时间。
    PRIMARY KEY (id), -- 主键索引；保证每条粉丝关系有唯一ID。
    UNIQUE KEY uk_to_from (to_user_id, from_user_id), -- 被关注者和粉丝唯一索引；防止同一个粉丝关系重复记录。
    KEY idx_to_created (to_user_id, created_at, from_user_id, rel_status), -- 被关注者维度列表索引；加快查询“谁关注了我”并按时间排序。
    KEY idx_from (from_user_id, to_user_id, rel_status) -- 关注者维度辅助索引；加快查询某个用户是否关注了另一个用户。
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='粉丝关系表：关注我的人'; -- 指定表引擎、字符集、排序规则和表说明。
