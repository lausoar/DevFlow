package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 工作流状态。
 *
 * category 是报表口径的关键：客户可以自定义任意状态名，
 * 但每个状态必须归入 TODO / IN_PROGRESS / DONE 三类，
 * 燃尽、速度、健康度、看板进度全部按 category 聚合，而不是按状态码。
 */
@Data
@TableName("workflow_status")
public class WorkflowStatus {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String workflowId;

    /** 状态码：存进 task.status / bug.status / requirement.status 的值 */
    private String code;

    /** 展示名 */
    private String name;

    /** 归一分类: TODO / IN_PROGRESS / DONE */
    private String category;

    private String color;

    private Integer sort;

    /** 新建时的初始状态 */
    private Integer isInitial;

    /** 终态（如已完成/已关闭） */
    private Integer isTerminal;

    /** 归一分类常量 */
    public static final String CATEGORY_TODO = "TODO";
    public static final String CATEGORY_IN_PROGRESS = "IN_PROGRESS";
    public static final String CATEGORY_DONE = "DONE";

    /** 是否已完结（报表里算作"完成"） */
    public boolean isDoneCategory() {
        return CATEGORY_DONE.equals(category);
    }
}
