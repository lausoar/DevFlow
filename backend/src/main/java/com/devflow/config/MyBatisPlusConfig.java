package com.devflow.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis Plus 配置
 */
@Configuration
public class MyBatisPlusConfig {

    /**
     * 单页最大条数。
     *
     * 之前没有任何上限：`?pageSize=100000` 会原样拼进 LIMIT，一个登录用户
     * 就能把整张表拉进内存，分页形同虚设。前端实际最高只用 200（看板一次
     * 拉满），QueryController 也显式 clamp 到 200，所以 200 是既有契约里
     * 的最大值，取它作为全局上限不会削减任何真实用法。
     */
    public static final long MAX_PAGE_SIZE = 200;

    /** 分页 + 乐观锁插件 */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 超过上限时把 pageSize 夹到 maxLimit（而不是报错），
        // 这样前端传大值仍然能用，只是拿不到超出上限的数据
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        // 页码超出总页数时回到第一页，避免空列表让前端误以为"没有数据"
        pagination.setOverflow(true);
        interceptor.addInnerInterceptor(pagination);
        // 乐观锁：实体上带 @Version 的字段会参与 WHERE 条件。
        // 版本值为 null 的老调用点（只 set 了 id 的 patch 对象）不受影响，
        // 所以可以逐步接入，不需要一次性改完所有写入路径。
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    /** 自动填充创建/更新时间 */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
