[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$Path,
    [Parameter(Mandatory=$true)][string]$SignTool,
    [string]$CertificateThumbprint = $env:FASTREMOTE_WINDOWS_CERTIFICATE_THUMBPRINT,
    [string]$TimestampUrl = 'http://timestamp.acs.microsoft.com',
    [switch]$VerifyOnly
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$target = Get-Item -LiteralPath $Path
$SignTool = (Get-Item -LiteralPath $SignTool).FullName
if (!$VerifyOnly) {
    if ($CertificateThumbprint -notmatch '^[0-9a-fA-F]{40}$') { throw 'A certificate thumbprint is required.' }
    $cert = Get-Item -LiteralPath "Cert:\CurrentUser\My\$CertificateThumbprint"
    if (!$cert.HasPrivateKey -or $cert.NotAfter -le (Get-Date) -or $cert.NotBefore -gt (Get-Date)) {
        throw 'The current-user signing certificate is not usable.'
    }
    if ($target.PSIsContainer -and (Test-Path (Join-Path $target.FullName 'app\fastremote-integrity.json'))) {
        throw 'Sign the image BEFORE generating the integrity manifest. Refusing to invalidate a sealed image.'
    }
}
$parentDirectory = if ($target.PSIsContainer) { $target.Parent.FullName } else { $target.Directory.FullName }
$scratch = Join-Path $parentDirectory ('windows-sign-work-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $scratch | Out-Null
$script:verified = 0
$script:signed = 0
function Invoke-SignTool([string[]]$Arguments) {
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $SignTool
    $start.Arguments = ($Arguments | ForEach-Object { '"' + $_.Replace('"', '\"') + '"' }) -join ' '
    $start.UseShellExecute = $false
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = [Diagnostics.Process]::Start($start)
    try {
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (!$process.WaitForExit(120000)) {
            $process.Kill()
            $process.WaitForExit()
            Write-Output $stdout.Result
            Write-Output $stderr.Result
            throw 'SignTool exceeded the 120-second timeout; no release may proceed.'
        }
        Write-Output $stdout.Result
        Write-Output $stderr.Result
        if ($process.ExitCode -ne 0) { throw "SignTool failed with exit code $($process.ExitCode)" }
    } finally { $process.Dispose() }
}
function Test-Pe([System.IO.Stream]$stream) {
    if ($stream.Length -lt 64) { return $false }
    $reader = [System.IO.BinaryReader]::new($stream, [Text.Encoding]::UTF8, $true)
    try {
        $stream.Position = 0
        if ($reader.ReadUInt16() -ne 0x5a4d) { return $false }
        $stream.Position = 60
        $offset = $reader.ReadUInt32()
        if ($offset -gt $stream.Length - 4) { return $false }
        $stream.Position = $offset
        return $reader.ReadUInt32() -eq 0x4550
    } finally { $reader.Dispose() }
}
function Confirm-Signature([string]$file) {
    $signature = Get-AuthenticodeSignature -LiteralPath $file
    if ($signature.Status -eq 'NotSigned' -and !$VerifyOnly) {
        # jpackage marks its launcher read-only. Change only this packaged file and restore its attributes.
        $attributes = [IO.File]::GetAttributes($file)
        try {
            [IO.File]::SetAttributes($file, $attributes -band (-bnot [IO.FileAttributes]::ReadOnly))
            Invoke-SignTool @('sign', '/fd', 'SHA256', '/sha1', $CertificateThumbprint, '/tr', $TimestampUrl, '/td', 'SHA256', $file)
        } finally { [IO.File]::SetAttributes($file, $attributes) }
        $signature = Get-AuthenticodeSignature -LiteralPath $file
        if ($signature.SignerCertificate.Thumbprint -ne $CertificateThumbprint -or !$signature.TimeStamperCertificate) {
            throw "Unexpected signer or missing timestamp: $file"
        }
        $script:signed++
    }
    if ($signature.Status -ne 'Valid') { throw "Invalid signature ($($signature.Status)): $file" }
    Invoke-SignTool @('verify', '/pa', '/all', $file)
    $script:verified++
}
if (!$target.PSIsContainer) {
    if ($target.Extension -notin @('.msi', '.exe')) { throw 'Expected an MSI/EXE or an App Image directory.' }
    if ($target.Extension -eq '.msi') {
        # jpackage adds native custom actions outside the App Image (notably JpCaDll).
        # Update only the Binary table in a transaction on a copy, BEFORE signing the MSI envelope.
        $msiCopy = Join-Path $scratch 'installer.msi'
        Copy-Item -LiteralPath $target.FullName -Destination $msiCopy
        $installer = New-Object -ComObject WindowsInstaller.Installer
        $database = $null
        $view = $null
        $changedMsi = $false
        $msiScratchFiles = [Collections.Generic.List[string]]::new()
        try {
            $mode = if ($VerifyOnly) { 0 } else { 1 }
            $database = $installer.OpenDatabase($msiCopy, $mode)
            $view = $database.OpenView('SELECT `Name`, `Data` FROM `Binary`')
            $view.Execute()
            while ($record = $view.Fetch()) {
                try {
                    $length = $record.DataSize(2)
                    if ($length -gt 67108864) { throw 'MSI Binary exceeds 64 MiB limit' }
                    $bytes = [Text.Encoding]::GetEncoding(28591).GetBytes($record.ReadStream(2, $length, 1))
                    $memory = [IO.MemoryStream]::new($bytes, $false)
                    try { $isPe = Test-Pe $memory } finally { $memory.Dispose() }
                    if (!$isPe) { continue }
                    $binary = Join-Path $scratch ([guid]::NewGuid().ToString('N') + '.dll')
                    $msiScratchFiles.Add($binary)
                    [IO.File]::WriteAllBytes($binary, $bytes)
                    $before = (Get-FileHash -LiteralPath $binary).Hash
                    Confirm-Signature $binary
                    if ((Get-FileHash -LiteralPath $binary).Hash -ne $before) {
                        if ((Get-AuthenticodeSignature $target.FullName).Status -ne 'NotSigned') {
                            throw 'Refusing to rewrite an already signed MSI. Rebuild the installer first.'
                        }
                        $record.SetStream(2, $binary)
                        $view.Modify(2, $record)
                        $changedMsi = $true
                    }
                } finally { [Runtime.InteropServices.Marshal]::FinalReleaseComObject($record) | Out-Null }
            }
            if ($changedMsi) { $database.Commit() }
        } finally {
            if ($view) { $view.Close(); [Runtime.InteropServices.Marshal]::FinalReleaseComObject($view) | Out-Null }
            if ($database) { [Runtime.InteropServices.Marshal]::FinalReleaseComObject($database) | Out-Null }
            [Runtime.InteropServices.Marshal]::FinalReleaseComObject($installer) | Out-Null
        }
        if ($changedMsi) { Copy-Item -LiteralPath $msiCopy -Destination $target.FullName -Force }
        foreach ($binary in $msiScratchFiles) { Remove-Item -LiteralPath $binary }
        Remove-Item -LiteralPath $msiCopy
    }
    Confirm-Signature $target.FullName
} else {
    foreach ($file in Get-ChildItem -LiteralPath $target.FullName -Recurse -File) {
        $stream = [IO.File]::OpenRead($file.FullName)
        try { $isPe = Test-Pe $stream } finally { $stream.Dispose() }
        if ($isPe) { Confirm-Signature $file.FullName }
        if ($file.Extension -ne '.jar') { continue }
        # Work only on packaged copies, never dependency caches. Commit a changed JAR only after every PE verifies.
        $copy = Join-Path $scratch ([guid]::NewGuid().ToString('N') + '.jar')
        Copy-Item -LiteralPath $file.FullName -Destination $copy
        $archive = [IO.Compression.ZipFile]::Open($copy, [IO.Compression.ZipArchiveMode]::Update)
        $changed = $false
        try {
            foreach ($entry in @($archive.Entries)) {
                if ($entry.Length -lt 64) { continue }
                $memory = [IO.MemoryStream]::new()
                $inputStream = $entry.Open()
                try { $inputStream.CopyTo($memory) } finally { $inputStream.Dispose() }
                try { $isPe = Test-Pe $memory } finally { $memory.Dispose() }
                if (!$isPe) { continue }
                $binary = Join-Path $scratch ([guid]::NewGuid().ToString('N') + '.dll')
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $binary)
                $before = (Get-FileHash -LiteralPath $binary -Algorithm SHA256).Hash
                Confirm-Signature $binary
                if ((Get-FileHash -LiteralPath $binary -Algorithm SHA256).Hash -ne $before) {
                    if (@($archive.Entries | Where-Object FullName -match '^META-INF/[^/]+\.(SF|RSA|DSA|EC)$').Count) {
                        throw "Cannot rewrite a JAR carrying Java signatures: $($file.Name)"
                    }
                    $name = $entry.FullName
                    $time = $entry.LastWriteTime
                    $entry.Delete()
                    $newEntry = [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $binary, $name)
                    $newEntry.LastWriteTime = $time
                    $changed = $true
                }
                Remove-Item -LiteralPath $binary
            }
        } finally { $archive.Dispose() }
        if ($changed) { Copy-Item -LiteralPath $copy -Destination $file.FullName -Force }
        Remove-Item -LiteralPath $copy
    }
}
# Only the explicitly created, now-empty scratch directory is removed. Failed runs retain diagnostic files.
if ($script:verified -eq 0) { throw 'No signable files were verified.' }
Remove-Item -LiteralPath $scratch
Write-Output "AUTHENTICODE_OK verified=$script:verified newlySigned=$script:signed target=$($target.FullName)"
