package com.devflow.service;

import com.devflow.entity.User;

/**
 * 用户服务接口
 */
public interface UserService {

    User getById(String id);

    User updateProfile(String userId, User updates);

    void changePassword(String userId, String oldPassword, String newPassword);

    User updateAvatar(String userId, String avatarUrl);
}
