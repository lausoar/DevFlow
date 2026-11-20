package com.devflow.service;

import com.devflow.entity.TriageItem;

import java.util.List;
import java.util.Map;

/**
 * 分诊收件箱：外部输入（游客 Bug / 建议）进入正式列表前的闸门。
 *
 * 与"直接进 Bug 列表"的区别在于三个明确动作：
 * - accept：把待确认的游客 Bug 转为正式条目；
 * - decline：明确拒绝并记录首响时间（不删除，保留可追溯）；
 * - merge：作为重复项合入已有 Bug，源记录保留并记录合并去向。
 */
public interface TriageService {

    /**
     * 入箱（幂等）。由游客提交入口调用，与源记录写入同一事务。
     * 返回 null 表示该条目已在箱内。
     */
    TriageItem enqueue(String projectId, String source, String sourceRefId, String title,
                       String submitterName);

    /** 项目收件箱列表（可按状态过滤） */
    List<Map<String, Object>> list(String projectId, String status);

    /** 各状态计数（侧边栏徽标/筛选用） */
    Map<String, Long> counts(String projectId);

    /** 接受：游客 Bug 转为正式；建议类需要调用方先转化再接受 */
    Map<String, Object> accept(String itemId, String operatorId);

    /** 拒绝 */
    Map<String, Object> decline(String itemId, String operatorId, String reason);

    /** 合并到已有 Bug */
    Map<String, Object> merge(String itemId, String targetBugId, String operatorId);

    /** 对外部提交做相似度提示（只提示，不自动合并） */
    List<Map<String, Object>> similar(String itemId);
}
