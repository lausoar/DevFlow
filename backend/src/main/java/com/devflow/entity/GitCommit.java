package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Git 提交记录（webhook 入站）
 */
@Data
@TableName("git_commit")
public class GitCommit {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 项目ID */
    private String projectId;

    /** 提交 SHA */
    private String sha;

    /** 提交说明 */
    private String message;

    /** 作者 */
    private String authorName;

    /** 提交链接 */
    private String webUrl;

    /** 推送时间 */
    private LocalDateTime pushedAt;
}
