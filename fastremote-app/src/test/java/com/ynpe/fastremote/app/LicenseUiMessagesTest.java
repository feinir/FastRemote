// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.ynpe.fastremote.domain.settings.InterfaceLanguage;
import com.ynpe.fastremote.domain.license.LicenseStatus;
import com.ynpe.fastremote.i18n.Localizer;
import org.junit.jupiter.api.Test;

final class LicenseUiMessagesTest {
    private final Localizer localizer = Localizer.forLanguage(InterfaceLanguage.SIMPLIFIED_CHINESE);

    @Test
    void mapsOnlineFailuresToUserFacingMessages() {
        assertEquals("无法连接许可证服务器，请检查网络后重试。",
                LicenseUiMessages.errorCode(localizer, "NETWORK_UNAVAILABLE"));
        assertEquals("许可证无效或当前不可用，请核对后重试。",
                LicenseUiMessages.errorCode(localizer, "LICENSE_INVALID"));
        assertEquals("许可证服务器返回了无效响应，请稍后重试。",
                LicenseUiMessages.errorCode(localizer, "ENTITLEMENT_REJECTED"));
        assertEquals("离线激活码无效，或不属于当前设备。",
                LicenseUiMessages.errorCode(localizer, "OFFLINE_LICENSE_INVALID"));
        assertEquals("许可证服务器返回了无效响应，请稍后重试。",
                LicenseUiMessages.errorCode(localizer, "REQUEST_ENCRYPTION_KEY_ID_UNSUPPORTED"));
        assertEquals("当前设备的试用已经结束。如需再次试用，请联系支持人员重新授权。",
                LicenseUiMessages.errorCode(localizer, "TRIAL_ALREADY_EXISTS"));
        assertFalse(LicenseUiMessages.errorCode(localizer, "AGENT_UNAVAILABLE").contains("Exception"));
    }

    @Test
    void explainsWhenOnlineRevalidationIsRequired() {
        assertEquals("需要联网检查许可证状态。",
                LicenseUiMessages.status(localizer, LicenseStatus.ONLINE_REVALIDATION_REQUIRED));
    }

    @Test
    void distinguishesInvalidLocalEntitlementFromReleaseFileIntegrity() {
        assertEquals("本地许可证数据无法通过签名验证，请重新激活 FastRemote。",
                LicenseUiMessages.status(localizer, LicenseStatus.INTEGRITY_ERROR));
    }
}
