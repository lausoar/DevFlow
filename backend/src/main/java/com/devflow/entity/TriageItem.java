package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 分诊收件箱条目：外部输入（游客 Bug / 建议）进入正式列表前的闸门。
 *
 * 源数据不复制、不改写：triage_item 只记录"有待处理""谁在什么时候处理""结论指向哪条记录"。
 * (source, source_ref_id) 唯一键保证同一条外部提交只入箱一次。
 */
@Data
@TableName("triage_item")
public class TriageItem {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_DECLINED = "DECLINED";
    public static final String STATUS_MERGED = "MERGED";

    public static final String SOURCE_GUEST_BUG = "GUEST_BUG";
    public static final String SOURCE_GUEST_FEEDBACK = "GUEST_FEEDBACK";
    public static final String SOURCE_USER_FEEDBACK = "USER_FEEDBACK";

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String projectId;
    private String enterpriseId;
    private String source;
    private String sourceRefId;
    private String title;
    private String submitterName;

    /** PENDING / ACCEPTED / DECLINED / MERGED */
    private String status;

    /** 接受或合并后指向的 Bug */
    private String resultRefId;

    private String decidedBy;
    private LocalDateTime decidedAt;

    /** 首次响应截止时间（项目未配置 SLA 时为空） */
    private LocalDateTime slaDueAt;

    /** 超时提醒发送时间：原子占用，避免多实例重复提醒 */
    private LocalDateTime slaNotifiedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
