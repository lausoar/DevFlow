package com.devflow.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 企业四级角色体系纯逻辑测试（权限收敛后的核心防越权规则）
 */
@DisplayName("EnterpriseRoles 四级角色")
class EnterpriseRolesTest {

    @Test
    @DisplayName("等级排序：超管100 > 管理员80 > 经理60 > 成员10")
    void levelOrder() {
        assertThat(EnterpriseRoles.level(EnterpriseRoles.SUPER_ADMIN)).isEqualTo(100);
        assertThat(EnterpriseRoles.level(EnterpriseRoles.ADMIN)).isEqualTo(80);
        assertThat(EnterpriseRoles.level(EnterpriseRoles.MANAGER)).isEqualTo(60);
        assertThat(EnterpriseRoles.level(EnterpriseRoles.MEMBER)).isEqualTo(10);
    }

    @Test
    @DisplayName("未知角色与 null 按最低等级处理")
    void unknownRoleFallsToMemberLevel() {
        assertThat(EnterpriseRoles.level("DIRECTOR")).isEqualTo(10);
        assertThat(EnterpriseRoles.level(null)).isEqualTo(10);
        assertThat(EnterpriseRoles.level("HACKER")).isEqualTo(10);
    }

    @Test
    @DisplayName("allRoles 按等级降序，供权限矩阵展示")
    void allRolesDescending() {
        assertThat(EnterpriseRoles.allRoles()).containsExactly(
                "SUPER_ADMIN", "ADMIN", "MANAGER", "MEMBER");
    }

    @Test
    @DisplayName("isValid：收敛后的 4 角色合法，旧 7 级角色与 null 非法")
    void isValidRoles() {
        assertThat(EnterpriseRoles.isValid("SUPER_ADMIN")).isTrue();
        assertThat(EnterpriseRoles.isValid("ADMIN")).isTrue();
        assertThat(EnterpriseRoles.isValid("MANAGER")).isTrue();
        assertThat(EnterpriseRoles.isValid("MEMBER")).isTrue();
        // 角色收敛后旧职级不再合法（授予 DIRECTOR 应被后端拒绝）
        assertThat(EnterpriseRoles.isValid("DIRECTOR")).isFalse();
        assertThat(EnterpriseRoles.isValid("SUPERVISOR")).isFalse();
        assertThat(EnterpriseRoles.isValid("LEADER")).isFalse();
        assertThat(EnterpriseRoles.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("canManage：经理及以上可管理，成员与未知角色不可")
    void canManageThreshold() {
        assertThat(EnterpriseRoles.canManage(EnterpriseRoles.SUPER_ADMIN)).isTrue();
        assertThat(EnterpriseRoles.canManage(EnterpriseRoles.ADMIN)).isTrue();
        assertThat(EnterpriseRoles.canManage(EnterpriseRoles.MANAGER)).isTrue();
        assertThat(EnterpriseRoles.canManage(EnterpriseRoles.MEMBER)).isFalse();
        assertThat(EnterpriseRoles.canManage("UNKNOWN")).isFalse();
    }

    @Test
    @DisplayName("canOperate：目标等级须严格低于操作者（同级不可互管）")
    void canOperateStrictlyBelow() {
        assertThat(EnterpriseRoles.canOperate(EnterpriseRoles.SUPER_ADMIN, EnterpriseRoles.MEMBER)).isTrue();
        assertThat(EnterpriseRoles.canOperate(EnterpriseRoles.ADMIN, EnterpriseRoles.MANAGER)).isTrue();
        // 同级不可互管：防止管理员改管理员
        assertThat(EnterpriseRoles.canOperate(EnterpriseRoles.ADMIN, EnterpriseRoles.ADMIN)).isFalse();
        assertThat(EnterpriseRoles.canOperate(EnterpriseRoles.SUPER_ADMIN, EnterpriseRoles.SUPER_ADMIN)).isFalse();
        // 下级不能操作上级
        assertThat(EnterpriseRoles.canOperate(EnterpriseRoles.MEMBER, EnterpriseRoles.SUPER_ADMIN)).isFalse();
    }

    @Test
    @DisplayName("canGrant：只能授予低于自己的合法角色，不能提权到同级或以上")
    void canGrantOnlyLowerValidRoles() {
        assertThat(EnterpriseRoles.canGrant(EnterpriseRoles.SUPER_ADMIN, EnterpriseRoles.ADMIN)).isTrue();
        assertThat(EnterpriseRoles.canGrant(EnterpriseRoles.MANAGER, EnterpriseRoles.MEMBER)).isTrue();
        // 越权提权防护
        assertThat(EnterpriseRoles.canGrant(EnterpriseRoles.ADMIN, EnterpriseRoles.SUPER_ADMIN)).isFalse();
        assertThat(EnterpriseRoles.canGrant(EnterpriseRoles.MANAGER, EnterpriseRoles.MANAGER)).isFalse();
        assertThat(EnterpriseRoles.canGrant(EnterpriseRoles.MEMBER, EnterpriseRoles.MANAGER)).isFalse();
        // 非法角色不可授予（收敛后的旧职级）
        assertThat(EnterpriseRoles.canGrant(EnterpriseRoles.SUPER_ADMIN, "DIRECTOR")).isFalse();
        assertThat(EnterpriseRoles.canGrant(EnterpriseRoles.SUPER_ADMIN, null)).isFalse();
    }
}
