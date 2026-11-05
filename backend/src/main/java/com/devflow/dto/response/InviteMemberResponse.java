package com.devflow.dto.response;

import com.devflow.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 邀请成员响应：新账号信息 + 一次性展示的初始密码（明文仅此一次返回，请邀请人复制告知对方）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InviteMemberResponse {

    private User user;

    /** 初始密码（明文仅返回一次） */
    private String initialPassword;
}
