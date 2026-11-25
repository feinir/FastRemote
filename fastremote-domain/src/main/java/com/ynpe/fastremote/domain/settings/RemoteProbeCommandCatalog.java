// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.settings;

import java.util.List;
import java.util.Map;

/** User-visible catalog of the fixed commands used by the first-stage server probe. */
public final class RemoteProbeCommandCatalog {
    private static final Map<String, List<String>> COMMANDS = Map.of(
            "Linux", List.of(
                    "uname -s", "hostname", "whoami", "pwd -P",
                    "getent passwd \"$(id -u)\" | cut -d: -f7（$SHELL 为空时）",
                    "uname -srm", "uname -a", "uname -m", "cat /etc/os-release",
                    "getconf _NPROCESSORS_ONLN",
                    "awk -F: '/^(model name|Hardware|Processor)/ {sub(/^[ \\t]+/, \"\", $2); print $2; exit}' /proc/cpuinfo",
                    "awk '/^MemTotal:|^MemAvailable:/' /proc/meminfo", "awk '{print $1}' /proc/uptime",
                    "ip -o -4 address show scope global", "ip -o -6 address show scope global",
                    "hostname -I（无 ip 命令时）"),
            "macOS", List.of(
                    "uname -s", "hostname", "whoami", "pwd -P", "printf '%s' \"$SHELL\"",
                    "uname -srm", "uname -a", "uname -m", "sw_vers -productName",
                    "sw_vers -productVersion", "sw_vers -buildVersion", "sysctl -n hw.logicalcpu",
                    "sysctl -n machdep.cpu.brand_string（失败时 sysctl -n hw.model）",
                    "sysctl -n hw.memsize", "sysctl -n hw.pagesize", "vm_stat",
                    "sysctl -n kern.boottime", "ifconfig"),
            "FreeBSD", List.of(
                    "uname -s", "hostname", "whoami", "pwd -P", "printf '%s' \"$SHELL\"",
                    "uname -srm", "uname -a", "uname -m", "freebsd-version -u",
                    "sysctl -n hw.ncpu", "sysctl -n hw.model", "sysctl -n hw.physmem",
                    "sysctl -n hw.pagesize", "sysctl -n vm.stats.vm.v_free_count",
                    "sysctl -n kern.boottime", "ifconfig"),
            "Windows", List.of(
                    "[Environment]::MachineName", "[Environment]::OSVersion.VersionString",
                    "$env:PROCESSOR_ARCHITECTURE", "whoami", "Get-Location",
                    "Get-CimInstance Win32_OperatingSystem",
                    "Get-CimInstance Win32_ComputerSystem", "Get-CimInstance Win32_Processor",
                    "Get-CimInstance Win32_NetworkAdapterConfiguration",
                    "Get-WmiObject Win32_*（无 Get-CimInstance 时）",
                    "ver（无 PowerShell 时回退 CMD）"));

    private RemoteProbeCommandCatalog() {
    }

    public static List<String> systems() {
        return List.of("Linux", "macOS", "FreeBSD", "Windows");
    }

    public static List<String> commands(String system) {
        return COMMANDS.getOrDefault(system, List.of());
    }

    public static String displayText(String system) {
        return String.join("\n", commands(system));
    }
}
