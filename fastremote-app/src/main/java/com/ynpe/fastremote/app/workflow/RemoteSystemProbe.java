// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import com.ynpe.fastremote.domain.automation.RemoteHostSystem;
import com.ynpe.fastremote.domain.terminal.RemoteCommandResult;
import com.ynpe.fastremote.domain.terminal.TerminalConnection;
import com.ynpe.fastremote.domain.settings.RemoteProbeSettings;
import com.ynpe.fastremote.ui.ServerOverview;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Bounded, non-interactive server facts probe using short-lived SSH exec channels. */
final class RemoteSystemProbe {
    static final Duration TOTAL_TIMEOUT = Duration.ofSeconds(30);
    static final int MAXIMUM_OUTPUT_BYTES = 256 * 1024;
    private static final Duration ATTEMPT_TIMEOUT = Duration.ofSeconds(10);
    private static final String PREFIX = "FRP1_";
    private static final int MAXIMUM_VALUE_CHARS = 4096;
    private static final int MAXIMUM_NETWORKS = 64;
    private static final Set<String> KNOWN_KEYS = Set.of(
            "OS", "HOST", "USER", "WORK_DIR", "LOGIN_SHELL", "SYSTEM", "UNAME_ALL", "ARCH",
            "OS_PRETTY", "OS_RELEASE_PRETTY_NAME", "OS_RELEASE_NAME", "OS_RELEASE_VERSION_ID",
            "CPU_COUNT", "CPU_MODEL", "MEM_TOTAL_KB", "MEM_AVAILABLE_KB",
            "MEM_TOTAL_BYTES", "MEM_AVAILABLE_BYTES", "UPTIME_SECONDS", "NET");

    private static final String POSIX_COMMAND = """
            HISTFILE=/dev/null
            HISTSIZE=0
            export HISTFILE HISTSIZE
            fr_emit() {
              [ -n "$2" ] && printf 'FRP1_%s=%s\\n' "$1" "$2"
            }
            fr_os=$(uname -s 2>/dev/null) || exit 64
            [ -n "$fr_os" ] || exit 64
            fr_emit OS "$fr_os"
            fr_emit HOST "$(hostname 2>/dev/null || uname -n 2>/dev/null)"
            fr_emit USER "$(whoami 2>/dev/null)"
            fr_emit WORK_DIR "$(pwd -P 2>/dev/null)"
            fr_emit LOGIN_SHELL "${SHELL:-$(getent passwd "$(id -u 2>/dev/null)" 2>/dev/null | cut -d: -f7)}"
            fr_emit SYSTEM "$(uname -srm 2>/dev/null)"
            fr_emit UNAME_ALL "$(uname -a 2>/dev/null)"
            fr_emit ARCH "$(uname -m 2>/dev/null)"
            case "$fr_os" in
              Linux)
                if [ -r /etc/os-release ]; then
                  while IFS='=' read -r fr_key fr_value; do
                    case "$fr_key" in
                      PRETTY_NAME|NAME|VERSION_ID)
                        fr_value=${fr_value#\\\"}
                        fr_value=${fr_value%\\\"}
                        fr_emit "OS_RELEASE_$fr_key" "$fr_value"
                        ;;
                    esac
                  done < /etc/os-release
                fi
                fr_emit CPU_COUNT "$(getconf _NPROCESSORS_ONLN 2>/dev/null || grep -c '^processor' /proc/cpuinfo 2>/dev/null)"
                fr_emit CPU_MODEL "$(awk -F: '/^(model name|Hardware|Processor)/ {sub(/^[ \\t]+/, "", $2); print $2; exit}' /proc/cpuinfo 2>/dev/null)"
                awk '/^MemTotal:/ {print "FRP1_MEM_TOTAL_KB=" $2} /^MemAvailable:/ {print "FRP1_MEM_AVAILABLE_KB=" $2}' /proc/meminfo 2>/dev/null
                fr_emit UPTIME_SECONDS "$(awk '{printf "%.0f", $1}' /proc/uptime 2>/dev/null)"
                if command -v ip >/dev/null 2>&1; then
                  ip -o -4 address show scope global 2>/dev/null | awk '{print "FRP1_NET=" $2 "  " $4}'
                  ip -o -6 address show scope global 2>/dev/null | awk '{print "FRP1_NET=" $2 "  " $4}'
                elif command -v hostname >/dev/null 2>&1; then
                  hostname -I 2>/dev/null | awk '{$1=$1; if (length($0)) print "FRP1_NET=" $0}'
                fi
                ;;
              Darwin)
                fr_emit OS_PRETTY "$(sw_vers -productName 2>/dev/null) $(sw_vers -productVersion 2>/dev/null) ($(sw_vers -buildVersion 2>/dev/null))"
                fr_emit CPU_COUNT "$(sysctl -n hw.logicalcpu 2>/dev/null)"
                fr_emit CPU_MODEL "$(sysctl -n machdep.cpu.brand_string 2>/dev/null || sysctl -n hw.model 2>/dev/null)"
                fr_emit MEM_TOTAL_BYTES "$(sysctl -n hw.memsize 2>/dev/null)"
                fr_page_size=$(sysctl -n hw.pagesize 2>/dev/null)
                fr_available_pages=$(vm_stat 2>/dev/null | awk '/Pages free:|Pages inactive:|Pages speculative:/ {gsub("\\.", "", $3); total += $3} END {printf "%.0f", total}')
                if [ -n "$fr_page_size" ] && [ -n "$fr_available_pages" ]; then
                  fr_emit MEM_AVAILABLE_BYTES "$((fr_page_size * fr_available_pages))"
                fi
                fr_boot=$(sysctl -n kern.boottime 2>/dev/null | sed -n 's/.*sec = *\\([0-9][0-9]*\\).*/\\1/p')
                fr_now=$(date +%s 2>/dev/null)
                if [ -n "$fr_boot" ] && [ -n "$fr_now" ]; then fr_emit UPTIME_SECONDS "$((fr_now - fr_boot))"; fi
                ifconfig 2>/dev/null | awk '/^[[:alnum:]]/ {iface=$1; sub(/:$/, "", iface)} $1=="inet" && $2!="127.0.0.1" {print "FRP1_NET=" iface "  " $2} $1=="inet6" && $2!="::1" {print "FRP1_NET=" iface "  " $2}'
                ;;
              FreeBSD)
                fr_emit OS_PRETTY "FreeBSD $(freebsd-version -u 2>/dev/null || uname -r 2>/dev/null)"
                fr_emit CPU_COUNT "$(sysctl -n hw.ncpu 2>/dev/null)"
                fr_emit CPU_MODEL "$(sysctl -n hw.model 2>/dev/null)"
                fr_emit MEM_TOTAL_BYTES "$(sysctl -n hw.physmem 2>/dev/null)"
                fr_page_size=$(sysctl -n hw.pagesize 2>/dev/null)
                fr_free_pages=$(sysctl -n vm.stats.vm.v_free_count 2>/dev/null)
                if [ -n "$fr_page_size" ] && [ -n "$fr_free_pages" ]; then
                  fr_emit MEM_AVAILABLE_BYTES "$((fr_page_size * fr_free_pages))"
                fi
                fr_boot=$(sysctl -n kern.boottime 2>/dev/null | sed -n 's/.*sec = *\\([0-9][0-9]*\\).*/\\1/p')
                fr_now=$(date +%s 2>/dev/null)
                if [ -n "$fr_boot" ] && [ -n "$fr_now" ]; then fr_emit UPTIME_SECONDS "$((fr_now - fr_boot))"; fi
                ifconfig 2>/dev/null | awk '/^[[:alnum:]]/ {iface=$1; sub(/:$/, "", iface)} $1=="inet" && $2!="127.0.0.1" {print "FRP1_NET=" iface "  " $2} $1=="inet6" && $2!="::1" {print "FRP1_NET=" iface "  " $2}'
                ;;
              *)
                fr_emit CPU_COUNT "$(getconf _NPROCESSORS_ONLN 2>/dev/null)"
                ;;
            esac
            """;

    private static final String WINDOWS_POWERSHELL_SCRIPT = """
            $ErrorActionPreference = 'SilentlyContinue'
            [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
            $OutputEncoding = [Console]::OutputEncoding
            function Emit([string]$Key, $Value) {
              if ($null -eq $Value) { return }
              $Text = ([string]$Value) -replace '[\\r\\n\\t]', ' '
              if (-not [string]::IsNullOrWhiteSpace($Text)) { Write-Output ('FRP1_' + $Key + '=' + $Text.Trim()) }
            }
            Emit 'OS' 'Windows'
            Emit 'HOST' ([Environment]::MachineName)
            Emit 'USER' (& whoami 2>$null)
            Emit 'WORK_DIR' ((Get-Location).Path)
            Emit 'ARCH' $env:PROCESSOR_ARCHITECTURE
            $OsInfo = $null
            $Computer = $null
            $Processor = $null
            if (Get-Command Get-CimInstance -ErrorAction SilentlyContinue) {
              $OsInfo = Get-CimInstance Win32_OperatingSystem
              $Computer = Get-CimInstance Win32_ComputerSystem
              $Processor = Get-CimInstance Win32_Processor | Select-Object -First 1
              $Adapters = Get-CimInstance Win32_NetworkAdapterConfiguration | Where-Object { $_.IPEnabled }
            } elseif (Get-Command Get-WmiObject -ErrorAction SilentlyContinue) {
              $OsInfo = Get-WmiObject Win32_OperatingSystem
              $Computer = Get-WmiObject Win32_ComputerSystem
              $Processor = Get-WmiObject Win32_Processor | Select-Object -First 1
              $Adapters = Get-WmiObject Win32_NetworkAdapterConfiguration | Where-Object { $_.IPEnabled }
            }
            if ($null -ne $OsInfo) {
              $Summary = (($OsInfo.Caption, $OsInfo.Version, ('Build ' + $OsInfo.BuildNumber)) -join ' ').Trim()
              Emit 'OS_PRETTY' $Summary
              Emit 'SYSTEM' $Summary
              Emit 'UNAME_ALL' $Summary
              Emit 'MEM_TOTAL_KB' $OsInfo.TotalVisibleMemorySize
              Emit 'MEM_AVAILABLE_KB' $OsInfo.FreePhysicalMemory
              try { Emit 'UPTIME_SECONDS' ([int64]([DateTime]::Now - [DateTime]$OsInfo.LastBootUpTime).TotalSeconds) } catch {}
            } else {
              Emit 'SYSTEM' ([Environment]::OSVersion.VersionString)
              Emit 'UNAME_ALL' ([Environment]::OSVersion.VersionString)
            }
            Emit 'CPU_COUNT' $Computer.NumberOfLogicalProcessors
            Emit 'CPU_MODEL' $Processor.Name
            foreach ($Adapter in @($Adapters)) {
              foreach ($Address in @($Adapter.IPAddress)) {
                if ($Address -and $Address -ne '127.0.0.1' -and $Address -ne '::1') { Emit 'NET' (($Adapter.Description + '  ' + $Address)) }
              }
            }
            """;

    private static final String WINDOWS_CMD_COMMAND = "cmd.exe /d /q /c \""
            + "chcp 65001>nul"
            + "&echo FRP1_OS=Windows"
            + "&echo FRP1_SYSTEM=%OS%"
            + "&echo FRP1_UNAME_ALL=%OS%"
            + "&echo FRP1_ARCH=%PROCESSOR_ARCHITECTURE%"
            + "&echo FRP1_CPU_COUNT=%NUMBER_OF_PROCESSORS%"
            + "&echo FRP1_WORK_DIR=%CD%"
            + "&for /f \"delims=\" %i in ('whoami') do @echo FRP1_USER=%i"
            + "&for /f \"delims=\" %i in ('hostname') do @echo FRP1_HOST=%i"
            + "&for /f \"delims=\" %i in ('ver') do @echo FRP1_OS_PRETTY=%i\"";

    private RemoteSystemProbe() {
    }

    static ProbeResult probe(TerminalConnection connection) throws IOException, InterruptedException {
        return probe(connection, RemoteProbeSettings.defaults());
    }

    static ProbeResult probe(TerminalConnection connection, RemoteProbeSettings settings)
            throws IOException, InterruptedException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(settings, "settings");
        long deadline = System.nanoTime() + TOTAL_TIMEOUT.toNanos();
        int remainingBytes = MAXIMUM_OUTPUT_BYTES;
        IOException lastFailure = null;
        boolean truncated = false;
        for (String command : List.of(POSIX_COMMAND, powershellCommand(), WINDOWS_CMD_COMMAND)) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0 || remainingBytes <= 0) break;
            Duration timeout = Duration.ofNanos(Math.min(ATTEMPT_TIMEOUT.toNanos(), remainingNanos));
            try {
                RemoteCommandResult result = connection.executeCommand(command, timeout, remainingBytes);
                remainingBytes = Math.max(0, remainingBytes - outputBytes(result));
                truncated |= result.truncated();
                ProbeFacts facts = parseFacts(result.standardOutput());
                if (facts.recognized()) {
                    SupplementalResult supplemental = runSupplement(
                            connection, facts, settings, deadline, remainingBytes);
                    return new ProbeResult(
                            overview(facts, truncated || supplemental.truncated(), supplemental.lines()), facts);
                }
            } catch (IOException failure) {
                lastFailure = failure;
            }
        }
        throw new IOException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.265a85ebcb225050"), lastFailure);
    }

    static RemoteHostSystem detectSystem(TerminalConnection connection)
            throws InterruptedException {
        // The automation tool runs the built-in probes only. User-configured supplements belong to the GUI overview.
        try {
            ProbeResult result = probe(connection, RemoteProbeSettings.defaults());
            String os = result.facts().values().get("OS");
            String operatingSystem = os.equalsIgnoreCase("Darwin") ? "macOS" : os;
            var family = RemoteHostSystem.SystemFamily
                    .valueOf(result.overview().systemFamily().name());
            return new RemoteHostSystem(operatingSystem, family);
        } catch (IOException unavailable) {
            return RemoteHostSystem.unknown();
        }
    }

    static ProbeFacts parseFacts(String output) {
        Map<String, String> values = new LinkedHashMap<>();
        List<String> networks = new ArrayList<>();
        for (String line : Objects.requireNonNull(output, "output").split("\\R")) {
            if (!line.startsWith(PREFIX)) continue;
            int separator = line.indexOf('=', PREFIX.length());
            if (separator <= PREFIX.length()) continue;
            String key = line.substring(PREFIX.length(), separator);
            if (!KNOWN_KEYS.contains(key)) continue;
            String value = safeValue(line.substring(separator + 1));
            if (value.isEmpty()) continue;
            if (key.equals("NET")) {
                if (networks.size() < MAXIMUM_NETWORKS) networks.add(value);
            } else {
                values.putIfAbsent(key, value);
            }
        }
        return new ProbeFacts(Map.copyOf(values), List.copyOf(networks));
    }

    static ServerOverview overview(ProbeFacts facts, boolean truncated) {
        return overview(facts, truncated, List.of());
    }

    static ServerOverview overview(
            ProbeFacts facts, boolean truncated, List<SupplementalCommandResult> supplemental) {
        Map<String, String> values = facts.values();
        String osPretty = first(values, "OS_PRETTY", "OS_RELEASE_PRETTY_NAME", "SYSTEM", "OS");
        String header = first(values, "UNAME_ALL", "SYSTEM", "OS_PRETTY", "OS");
        String cpu = values.getOrDefault("CPU_COUNT", com.ynpe.fastremote.i18n.Texts.text("legacy.java.4d8c1c5b42830791")) + com.ynpe.fastremote.i18n.Texts.text("legacy.java.c627246de5ff2a9e");
        if (values.containsKey("CPU_MODEL")) cpu += " · " + values.get("CPU_MODEL");
        StringBuilder text = new StringBuilder(header).append('\n')
                .append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.067a55520e207699")).append(values.getOrDefault("HOST", com.ynpe.fastremote.i18n.Texts.text("legacy.java.4d8c1c5b42830791"))).append('\n')
                .append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a2d4817d83ee7844")).append(values.getOrDefault("USER", com.ynpe.fastremote.i18n.Texts.text("legacy.java.4d8c1c5b42830791")))
                .append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3153b2ae159d428d")).append(values.getOrDefault("WORK_DIR", com.ynpe.fastremote.i18n.Texts.text("legacy.java.4d8c1c5b42830791"))).append('\n')
                .append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7f906cc0b0593072")).append(osPretty).append('\n')
                .append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.eb3a222d837fa976")).append(values.getOrDefault("SYSTEM", com.ynpe.fastremote.i18n.Texts.text("legacy.java.4d8c1c5b42830791")))
                .append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.8a7180061788fa85")).append(values.getOrDefault("ARCH", com.ynpe.fastremote.i18n.Texts.text("legacy.java.4d8c1c5b42830791"))).append('\n')
                .append("CPU   ").append(cpu).append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b775f03e1c517332")).append(memorySummary(values)).append('\n')
                .append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.921dcce851131aeb")).append(uptimeSummary(values.get("UPTIME_SECONDS"))).append('\n')
                .append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3ee71d2404dccef3"));
        if (facts.networks().isEmpty()) text.append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c4e299f3871c7865"));
        else text.append(String.join("；", facts.networks()));
        if (!supplemental.isEmpty()) {
            text.append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.16b3e6e0f1cde934"));
            for (SupplementalCommandResult command : supplemental) {
                text.append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e51a32bba589e28f")).append(command.name()).append("]：\n")
                        .append(command.lines().isEmpty() ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.8ae64c2c8ce582d0") : String.join("\n", command.lines()));
            }
        }
        if (truncated) text.append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4442fc841975ec07"));
        return new ServerOverview(text.toString(), systemFamily(facts));
    }

    static ServerOverview.SystemFamily systemFamily(ProbeFacts facts) {
        String operatingSystem = facts.values().getOrDefault("OS", "");
        if (operatingSystem.equalsIgnoreCase("Windows")) return ServerOverview.SystemFamily.WINDOWS;
        return facts.recognized() ? ServerOverview.SystemFamily.POSIX : ServerOverview.SystemFamily.UNKNOWN;
    }

    private static String powershellCommand() {
        String encoded = Base64.getEncoder().encodeToString(
                WINDOWS_POWERSHELL_SCRIPT.getBytes(StandardCharsets.UTF_16LE));
        return "powershell.exe -NoLogo -NoProfile -NonInteractive -EncodedCommand " + encoded;
    }

    private static SupplementalResult runSupplement(
            TerminalConnection connection,
            ProbeFacts facts,
            RemoteProbeSettings settings,
            long deadline,
            int remainingBytes) throws InterruptedException {
        String operatingSystem = facts.values().getOrDefault("OS", "");
        List<String> commands = settings.supplementFor(operatingSystem).lines()
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .limit(RemoteProbeSettings.MAX_SUPPLEMENT_COMMANDS)
                .toList();
        if (commands.isEmpty() || remainingBytes <= 0) {
            return new SupplementalResult(List.of(), remainingBytes <= 0);
        }
        List<SupplementalCommandResult> outputs = new ArrayList<>();
        boolean truncated = false;
        int budget = remainingBytes;
        for (String command : commands) {
            long remainingNanos = deadline - System.nanoTime();
            String name = supplementalCommandName(command);
            if (remainingNanos <= 0) {
                outputs.add(new SupplementalCommandResult(name, List.of(com.ynpe.fastremote.i18n.Texts.text("legacy.java.03d55b02830a3ba6"))));
                break;
            }
            if (budget <= 0) {
                truncated = true;
                break;
            }
            Duration timeout = Duration.ofNanos(Math.min(ATTEMPT_TIMEOUT.toNanos(), remainingNanos));
            String wrapped = operatingSystem.equalsIgnoreCase("Windows")
                    ? powershellSupplement(command)
                    : "{\n" + command + "\n} 2>&1";
            try {
                RemoteCommandResult result = connection.executeCommand(wrapped, timeout, budget);
                budget = Math.max(0, budget - outputBytes(result));
                truncated |= result.truncated();
                outputs.add(new SupplementalCommandResult(
                        name, safeSupplement(result.standardOutput(), result.standardError())));
            } catch (IOException failure) {
                outputs.add(new SupplementalCommandResult(name, List.of(com.ynpe.fastremote.i18n.Texts.text("legacy.java.cc8ceca59499dc5b"))));
            }
        }
        return new SupplementalResult(List.copyOf(outputs), truncated);
    }

    private static String supplementalCommandName(String command) {
        int separator = 0;
        while (separator < command.length() && !Character.isWhitespace(command.charAt(separator))) separator++;
        return safeValue(command.substring(0, separator));
    }

    private static String powershellSupplement(String command) {
        String script = "$ErrorActionPreference='Continue'; [Console]::OutputEncoding="
                + "New-Object System.Text.UTF8Encoding($false); & {\n" + command + "\n} 2>&1";
        return "powershell.exe -NoLogo -NoProfile -NonInteractive -EncodedCommand "
                + Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
    }

    private static List<String> safeSupplement(String standardOutput, String standardError) {
        List<String> result = new ArrayList<>();
        for (String value : List.of(standardOutput, standardError)) {
            for (String line : value.split("\\R")) {
                String safe = safeValue(line);
                if (!safe.isEmpty() && result.size() < 512) result.add(safe);
            }
        }
        return List.copyOf(result);
    }

    private static int outputBytes(RemoteCommandResult result) {
        long bytes = (long) result.standardOutput().getBytes(StandardCharsets.UTF_8).length
                + result.standardError().getBytes(StandardCharsets.UTF_8).length;
        return (int) Math.min(Integer.MAX_VALUE, bytes);
    }

    private static String safeValue(String value) {
        StringBuilder safe = new StringBuilder(Math.min(value.length(), MAXIMUM_VALUE_CHARS));
        value.codePoints().limit(MAXIMUM_VALUE_CHARS).forEach(codePoint -> {
            if (!Character.isISOControl(codePoint) || codePoint == ' ') safe.appendCodePoint(codePoint);
        });
        return safe.toString().strip();
    }

    private static String first(Map<String, String> values, String... keys) {
        for (String key : keys) {
            String value = values.get(key);
            if (value != null && !value.isBlank()) return value;
        }
        return com.ynpe.fastremote.i18n.Texts.text("legacy.java.4d8c1c5b42830791");
    }

    private static String memorySummary(Map<String, String> values) {
        try {
            long total = memoryBytes(values, "MEM_TOTAL_BYTES", "MEM_TOTAL_KB");
            long available = memoryBytes(values, "MEM_AVAILABLE_BYTES", "MEM_AVAILABLE_KB");
            if (total <= 0 || available < 0) return com.ynpe.fastremote.i18n.Texts.text("legacy.java.4d8c1c5b42830791");
            return formatBytes(total - Math.min(total, available)) + com.ynpe.fastremote.i18n.Texts.text("legacy.java.e556cd4e7f1b9e3e") + formatBytes(total) + com.ynpe.fastremote.i18n.Texts.text("legacy.java.05cc1d1d63f25adb");
        } catch (RuntimeException unavailable) {
            return com.ynpe.fastremote.i18n.Texts.text("legacy.java.4d8c1c5b42830791");
        }
    }

    private static long memoryBytes(Map<String, String> values, String bytesKey, String kilobytesKey) {
        if (values.containsKey(bytesKey)) return Long.parseLong(values.get(bytesKey));
        return Math.multiplyExact(Long.parseLong(values.get(kilobytesKey)), 1024L);
    }

    private static String formatBytes(long bytes) {
        return com.ynpe.fastremote.i18n.Texts.localizer().formatBytes(bytes);
    }

    private static String uptimeSummary(String secondsValue) {
        try {
            long seconds = Long.parseLong(secondsValue);
            long days = seconds / 86_400;
            long hours = seconds % 86_400 / 3_600;
            long minutes = seconds % 3_600 / 60;
            var localizer = com.ynpe.fastremote.i18n.Texts.localizer();
            StringBuilder text = new StringBuilder();
            if (days > 0) text.append(localizer.text(days == 1
                    ? "probe.uptime.day.one" : "probe.uptime.day.other", days));
            if (hours > 0 || days > 0) {
                if (!text.isEmpty()) text.append(' ');
                text.append(localizer.text(hours == 1
                        ? "probe.uptime.hour.one" : "probe.uptime.hour.other", hours));
            }
            if (!text.isEmpty()) text.append(' ');
            return text.append(localizer.text(minutes == 1
                    ? "probe.uptime.minute.one" : "probe.uptime.minute.other", minutes)).toString();
        } catch (RuntimeException unavailable) {
            return com.ynpe.fastremote.i18n.Texts.text("legacy.java.4d8c1c5b42830791");
        }
    }

    record ProbeFacts(Map<String, String> values, List<String> networks) {
        ProbeFacts {
            values = Map.copyOf(values);
            networks = List.copyOf(networks);
        }

        boolean recognized() {
            return values.containsKey("OS");
        }
    }

    record ProbeResult(ServerOverview overview, ProbeFacts facts) {
        ProbeResult {
            Objects.requireNonNull(overview, "overview");
            Objects.requireNonNull(facts, "facts");
        }
    }

    record SupplementalCommandResult(String name, List<String> lines) {
        SupplementalCommandResult {
            name = Objects.requireNonNull(name, "name");
            lines = List.copyOf(lines);
        }
    }

    private record SupplementalResult(List<SupplementalCommandResult> lines, boolean truncated) { }
}
