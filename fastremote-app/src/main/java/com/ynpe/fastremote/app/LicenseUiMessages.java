// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import com.ynpe.fastremote.domain.license.LicenseStatus;
import com.ynpe.fastremote.i18n.Localizer;
import com.ynpe.fastremote.licensing.GoAgentLicenseGateway.LicenseAgentException;

final class LicenseUiMessages {
    private LicenseUiMessages() { }

    static String error(Localizer localizer, Throwable error) {
        return errorCode(localizer, code(error));
    }

    static String errorCode(Localizer localizer, String code) {
        return switch (code) {
            case "NETWORK_UNAVAILABLE" -> localizer.text("license.error.network");
            case "LICENSE_INVALID" -> localizer.text("license.error.invalidCode");
            case "DEVICE_LIMIT_REACHED" -> localizer.text("license.error.deviceLimit");
            case "LICENSE_REVOKED" -> localizer.text("license.error.revoked");
            case "LICENSE_EXPIRED" -> localizer.text("license.error.expired");
            case "DEVICE_REBIND_REQUIRED" -> localizer.text("license.error.rebind");
            case "CLIENT_VERSION_BLOCKED" -> localizer.text("license.error.versionBlocked");
            case "SERVER_RESPONSE_INVALID", "ENTITLEMENT_REJECTED", "INTERNAL_ERROR",
                    "REQUEST_ENCRYPTION_KEY_ID_UNSUPPORTED", "REQUEST_ENCRYPTION_KEY_DISABLED",
                    "REQUEST_DECRYPTION_FAILED" ->
                    localizer.text("license.error.server");
            case "AGENT_UNAVAILABLE", "AGENT_TIMEOUT" -> localizer.text("license.error.agent");
            case "OFFLINE_LICENSE_INVALID" -> localizer.text("license.error.offlineInvalid");
            case "STATE_SAVE_FAILED" -> localizer.text("license.error.save");
            case "TRIAL_ALREADY_EXISTS" -> localizer.text("license.error.trialAlreadyUsed");
            default -> localizer.text("license.error.generic");
        };
    }

    static String status(Localizer localizer, LicenseStatus status) {
        return switch (status) {
            case TRIAL_AVAILABLE -> localizer.text("license.status.trialAvailable");
            case NEEDS_ACTIVATION -> localizer.text("license.status.notActivated");
            case EXPIRED -> localizer.text("license.error.expired");
            case REVOKED -> localizer.text("license.error.revoked");
            case DEVICE_LIMIT_REACHED -> localizer.text("license.error.deviceLimit");
            case DEVICE_REBIND_REQUIRED -> localizer.text("license.error.rebind");
            case CLOCK_REVALIDATION_REQUIRED -> localizer.text("license.status.clock");
            case ONLINE_REVALIDATION_REQUIRED -> localizer.text("license.status.onlineRevalidation");
            case CLIENT_VERSION_BLOCKED -> localizer.text("license.error.versionBlocked");
            case INTEGRITY_ERROR -> localizer.text("license.status.integrity");
            case AGENT_UNAVAILABLE -> localizer.text("license.error.agent");
            default -> localizer.text("license.error.generic");
        };
    }

    private static String code(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof LicenseAgentException agentError) return agentError.code();
            current = current.getCause();
        }
        return "";
    }
}
