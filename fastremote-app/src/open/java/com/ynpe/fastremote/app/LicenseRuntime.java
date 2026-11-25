// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import com.ynpe.fastremote.licensing.CommunityLicenseGateway;
import com.ynpe.fastremote.licensing.LicenseGateway;
import java.net.URI;
import java.nio.file.Path;

final class LicenseRuntime {
    private LicenseRuntime() { }

    static LicenseGateway create(Path applicationDataDirectory) {
        return new CommunityLicenseGateway();
    }

    static URI serviceUri() {
        return URI.create("https://download.ynpe.com");
    }
}
