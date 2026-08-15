[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet("setup", "start", "status", "pair", "firewall", "stop", "help")]
    [string]$Command = "help",

    [string]$LocalStorageRoot,
    [string]$ExternalStorageRoot,
    [string]$ExternalStorageName,
    [int]$ServerPort = 8787,
    [switch]$ServerOnly,
    [switch]$WithMobile,
    [switch]$Cli,
    [switch]$NonInteractive,
    [switch]$SkipInstall,
    [switch]$ConfigureFirewall,
    [switch]$NoOpen,
    [switch]$Remove
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ScriptBoundParameterNames = @($PSBoundParameters.Keys)

$RepositoryRoot = Split-Path -Parent $PSCommandPath
$StateRoot = Join-Path $RepositoryRoot ".shove-dev"
$ConfigPath = Join-Path $StateRoot "config.json"
$RuntimePath = Join-Path $StateRoot "runtime.json"
$LogRoot = Join-Path $StateRoot "logs"
$ServerRoot = Join-Path $RepositoryRoot "server"
$MobileRoot = Join-Path $RepositoryRoot "apps\mobile"
$PairingScript = Join-Path $RepositoryRoot "scripts\show-pairing-code.ps1"
$FirewallScript = Join-Path $RepositoryRoot "scripts\configure-firewall.ps1"

function Write-Heading([string]$Text) {
    Write-Host ""
    Write-Host $Text -ForegroundColor Green
}

function Write-Info([string]$Label, [string]$Value) {
    Write-Host ("{0,-18} {1}" -f $Label, $Value)
}

function Get-RequiredCommand([string]$Name, [string]$InstallHint) {
    $commandInfo = Get-Command $Name -ErrorAction SilentlyContinue
    if ($null -eq $commandInfo) {
        throw "Missing prerequisite: $Name. $InstallHint"
    }
    return $commandInfo.Source
}

function Get-NodeCommand {
    $onPath = Get-Command "node.exe" -ErrorAction SilentlyContinue
    if ($null -ne $onPath) { return $onPath.Source }
    $candidates = @(
        (Join-Path $env:LOCALAPPDATA "Programs\nodejs\node.exe"),
        (Join-Path $env:ProgramFiles "nodejs\node.exe")
    )
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    return $null
}

function Get-PnpmCommand {
    $node = Get-NodeCommand
    if ($node) {
        $besideNode = Join-Path (Split-Path -Parent $node) "pnpm.cmd"
        if (Test-Path -LiteralPath $besideNode) { return $besideNode }
    }
    $onPath = Get-Command "pnpm.cmd" -ErrorAction SilentlyContinue
    if ($null -ne $onPath) { return $onPath.Source }
    return $null
}

function Assert-Prerequisites([bool]$NeedMobile) {
    [void](Get-RequiredCommand "java.exe" "Install a Java 21 JDK, then open a new PowerShell window.")
    [void](Get-RequiredCommand "mvn.cmd" "Install Maven 3.6.3 or newer, then open a new PowerShell window.")
    if ($NeedMobile) {
        if (-not (Get-NodeCommand)) { throw "Missing prerequisite: node.exe. Install Node.js 20.19 or newer." }
        if (-not (Get-PnpmCommand)) { throw "Missing prerequisite: pnpm.cmd. Install pnpm 10." }
    }
}

function Repair-DuplicateProcessEnvironment {
    # Some managed development shells provide both Path and PATH. Windows treats
    # them as one variable, but Start-Process rejects the duplicate dictionary.
    $duplicates = [Environment]::GetEnvironmentVariables("Process").Keys |
        Group-Object { $_.ToString().ToUpperInvariant() } |
        Where-Object { $_.Count -gt 1 }
    foreach ($group in $duplicates) {
        $canonicalName = $group.Group[0].ToString()
        $value = [Environment]::GetEnvironmentVariable($canonicalName, "Process")
        foreach ($name in $group.Group) {
            [Environment]::SetEnvironmentVariable($name.ToString(), $null, "Process")
        }
        [Environment]::SetEnvironmentVariable($canonicalName, $value, "Process")
    }
}

function Resolve-ConfiguredPath([string]$Value) {
    $expanded = [Environment]::ExpandEnvironmentVariables($Value.Trim())
    if (-not [IO.Path]::IsPathRooted($expanded)) {
        $expanded = Join-Path $RepositoryRoot $expanded
    }
    return [IO.Path]::GetFullPath($expanded)
}

function Ensure-ConfiguredDirectory([string]$Path, [string]$Label) {
    $driveRoot = [IO.Path]::GetPathRoot($Path)
    if (-not (Test-Path -LiteralPath $driveRoot)) {
        Write-Warning "$Label is saved but its drive is not connected: $Path"
        return
    }
    [void](New-Item -ItemType Directory -Path $Path -Force)
}

function Read-Config {
    if (-not (Test-Path -LiteralPath $ConfigPath)) {
        throw "Shove is not configured yet. Run: .\shove.cmd setup"
    }
    return Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
}

function Save-Json([string]$Path, [object]$Value) {
    [void](New-Item -ItemType Directory -Path (Split-Path -Parent $Path) -Force)
    $json = $Value | ConvertTo-Json -Depth 6
    Set-Content -LiteralPath $Path -Value $json -Encoding UTF8
}

function Read-WithDefault([string]$Prompt, [string]$DefaultValue) {
    $answer = Read-Host "$Prompt [$DefaultValue]"
    if ([string]::IsNullOrWhiteSpace($answer)) { return $DefaultValue }
    return $answer.Trim()
}

function Read-Optional([string]$Prompt, [string]$DefaultValue) {
    $display = if ([string]::IsNullOrWhiteSpace($DefaultValue)) { "none" } else { $DefaultValue }
    $answer = Read-Host "$Prompt [$display]"
    if ([string]::IsNullOrWhiteSpace($answer)) { return $DefaultValue }
    if ($answer.Trim().ToLowerInvariant() -eq "none") { return "" }
    return $answer.Trim()
}

function Read-YesNo([string]$Prompt, [bool]$DefaultValue) {
    $display = if ($DefaultValue) { "Y/n" } else { "y/N" }
    $answer = Read-Host "$Prompt [$display]"
    if ([string]::IsNullOrWhiteSpace($answer)) { return $DefaultValue }
    switch ($answer.Trim().ToLowerInvariant()) {
        "y" { return $true }
        "yes" { return $true }
        "n" { return $false }
        "no" { return $false }
        default { throw "Enter yes or no." }
    }
}

function Invoke-MavenPackage {
    $maven = Get-RequiredCommand "mvn.cmd" "Install Maven 3.6.3 or newer."
    Write-Host "Packaging the Java server..." -ForegroundColor Cyan
    Push-Location $ServerRoot
    try {
        & $maven "-DskipTests" "package"
        if ($LASTEXITCODE -ne 0) { throw "The Java server build failed with exit code $LASTEXITCODE." }
    } finally {
        Pop-Location
    }
}

function Get-ServerJar {
    $jar = Get-ChildItem -LiteralPath (Join-Path $ServerRoot "target") -Filter "shove-it-server-*.jar" -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*.original" } |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
    if ($null -eq $jar) { return $null }
    return $jar
}

function Test-ServerBuildRequired {
    $jar = Get-ServerJar
    if ($null -eq $jar) { return $true }
    $newestSource = Get-ChildItem -LiteralPath (Join-Path $ServerRoot "src") -Recurse -File |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
    $pom = Get-Item -LiteralPath (Join-Path $ServerRoot "pom.xml")
    $newestInput = if ($newestSource.LastWriteTimeUtc -gt $pom.LastWriteTimeUtc) { $newestSource } else { $pom }
    return $newestInput.LastWriteTimeUtc -gt $jar.LastWriteTimeUtc
}

function Test-TcpPort([int]$Port, [int]$TimeoutMs = 500) {
    $client = New-Object Net.Sockets.TcpClient
    try {
        $result = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        if (-not $result.AsyncWaitHandle.WaitOne($TimeoutMs)) { return $false }
        $client.EndConnect($result)
        return $true
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Get-Health([int]$Port) {
    try {
        return Invoke-RestMethod -Uri "http://127.0.0.1:$Port/healthz" -TimeoutSec 2
    } catch {
        return $null
    }
}

function Wait-ForServer([int]$Port, [int]$TimeoutSeconds = 30) {
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $health = Get-Health $Port
        if ($null -ne $health -and $health.status -eq "ok") { return $health }
        Start-Sleep -Milliseconds 400
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    return $null
}

function ConvertTo-SubnetCidr([string]$Address, [int]$PrefixLength) {
    if ($PrefixLength -lt 1 -or $PrefixLength -gt 32) {
        throw "Unsupported IPv4 prefix length: $PrefixLength"
    }
    $addressBytes = [Net.IPAddress]::Parse($Address).GetAddressBytes()
    $networkBytes = [byte[]]::new(4)
    $bitsRemaining = $PrefixLength
    for ($index = 0; $index -lt 4; $index++) {
        $mask = if ($bitsRemaining -ge 8) {
            255
        } elseif ($bitsRemaining -le 0) {
            0
        } else {
            256 - [Math]::Pow(2, 8 - $bitsRemaining)
        }
        $networkBytes[$index] = [byte]([int]$addressBytes[$index] -band [int]$mask)
        $bitsRemaining -= 8
    }
    return "$([Net.IPAddress]::new($networkBytes))/$PrefixLength"
}

function Get-LanNetwork {
    try {
        $interfaces = [Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces() |
            Where-Object {
                $_.OperationalStatus -eq [Net.NetworkInformation.OperationalStatus]::Up -and
                $_.NetworkInterfaceType.ToString() -in @("Wireless80211", "Ethernet", "GigabitEthernet", "FastEthernetFx", "FastEthernetT")
            } |
            Sort-Object @{ Expression = { if ($_.NetworkInterfaceType.ToString() -eq "Wireless80211") { 0 } else { 1 } } }
        foreach ($networkInterface in $interfaces) {
            $properties = $networkInterface.GetIPProperties()
            $hasGateway = $properties.GatewayAddresses |
                Where-Object {
                    $_.Address.AddressFamily -eq [Net.Sockets.AddressFamily]::InterNetwork -and
                    $_.Address.IPAddressToString -ne "0.0.0.0"
                } |
                Select-Object -First 1
            if ($null -eq $hasGateway) { continue }

            $addressRecord = $properties.UnicastAddresses |
                Where-Object {
                    $_.Address.AddressFamily -eq [Net.Sockets.AddressFamily]::InterNetwork -and
                    $_.Address.IPAddressToString -match "^(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.)"
                } |
                Select-Object -First 1
            if ($null -ne $addressRecord) {
                $address = $addressRecord.Address.IPAddressToString
                $prefixLength = [int]$addressRecord.PrefixLength
                return [pscustomobject]@{
                    address = $address
                    prefixLength = $prefixLength
                    subnet = ConvertTo-SubnetCidr $address $prefixLength
                }
            }
        }
    } catch {
        # Fall through to the Windows network cmdlets below.
    }

    try {
        $configurations = Get-NetIPConfiguration -ErrorAction Stop |
            Where-Object { $null -ne $_.IPv4DefaultGateway -and $null -ne $_.IPv4Address }
        foreach ($configuration in $configurations) {
            $addressRecord = $configuration.IPv4Address |
                Where-Object { $_.IPAddress -match "^(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.)" } |
                Select-Object -First 1
            if ($null -ne $addressRecord) {
                return [pscustomobject]@{
                    address = [string]$addressRecord.IPAddress
                    prefixLength = [int]$addressRecord.PrefixLength
                    subnet = ConvertTo-SubnetCidr ([string]$addressRecord.IPAddress) ([int]$addressRecord.PrefixLength)
                }
            }
        }
    } catch {
        return $null
    }
    return $null
}

function Get-LanAddress {
    $network = Get-LanNetwork
    if ($null -ne $network) { return $network.address }

    return [Net.Dns]::GetHostAddresses([Net.Dns]::GetHostName()) |
        Where-Object { $_.AddressFamily -eq [Net.Sockets.AddressFamily]::InterNetwork } |
        ForEach-Object { $_.IPAddressToString } |
        Where-Object { $_ -match "^(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.)" } |
        Select-Object -First 1
}

function Invoke-Firewall([bool]$RemoveRules = $false) {
    [void](New-Item -ItemType Directory -Path $LogRoot -Force)
    $firewallLog = Join-Path $LogRoot "firewall.log"
    $action = if ($RemoveRules) { "Remove" } else { "Install" }
    $arguments = @(
        "-Action '$action'",
        "-LogPath '$($firewallLog.Replace("'", "''"))'"
    )

    if (-not $RemoveRules) {
        $config = Read-Config
        $network = Get-LanNetwork
        if ($null -eq $network) {
            throw "A private IPv4 home network and subnet could not be detected. Connect Wi-Fi and try again."
        }
        $java = Get-RequiredCommand "java.exe" "Install a Java 21 JDK."
        $arguments += "-Subnet '$($network.subnet.Replace("'", "''"))'"
        $arguments += "-ServerPort $([int]$config.serverPort)"
        $arguments += "-JavaProgram '$($java.Replace("'", "''"))'"
        if ([bool]$config.startMobile) {
            $node = Get-NodeCommand
            if (-not $node) { throw "Node.js is required for the Expo development firewall rule." }
            $arguments += "-IncludeExpo"
            $arguments += "-NodeProgram '$($node.Replace("'", "''"))'"
        }
    }

    $escapedScript = $FirewallScript.Replace("'", "''")
    $command = "& '$escapedScript' $($arguments -join ' ')"
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($command))
    Write-Host "Windows will ask permission to $($action.ToLowerInvariant()) Shove's narrow firewall rules." -ForegroundColor Cyan
    try {
        $process = Start-Process -FilePath "powershell.exe" `
            -ArgumentList "-NoProfile -ExecutionPolicy Bypass -EncodedCommand $encoded" `
            -Verb RunAs `
            -WindowStyle Hidden `
            -Wait `
            -PassThru
    } catch {
        throw "Windows firewall permission was cancelled or failed: $($_.Exception.Message)"
    }
    if ($process.ExitCode -ne 0) {
        throw "Windows firewall configuration failed with exit code $($process.ExitCode)."
    }

    if ($RemoveRules) {
        Write-Host "Shove firewall rules were removed." -ForegroundColor Green
    } else {
        Write-Info "Firewall subnet" $network.subnet
        Write-Info "Java rule" "TCP $($config.serverPort)"
        Write-Info "Expo dev rule" $(if ([bool]$config.startMobile) { "TCP 8081" } else { "not required" })
        Write-Host "Windows firewall is configured for this home network." -ForegroundColor Green
    }
    Write-Info "Firewall log" $firewallLog
}

function Test-RecordedProcess([object]$Record) {
    if ($null -eq $Record -or $null -eq $Record.pid) { return $false }
    $process = Get-Process -Id ([int]$Record.pid) -ErrorAction SilentlyContinue
    if ($null -eq $process) { return $false }
    try {
        $recorded = [DateTimeOffset]::Parse([string]$Record.startedAtUtc)
        $actual = [DateTimeOffset]$process.StartTime.ToUniversalTime()
        return [Math]::Abs(($actual - $recorded).TotalSeconds) -lt 2
    } catch {
        return $false
    }
}

function Stop-RecordedProcess([object]$Record, [string]$Label) {
    if (-not (Test-RecordedProcess $Record)) {
        Write-Host "$Label is already stopped."
        return
    }

    $processId = [int]$Record.pid
    if ($Record.kind -eq "java") {
        Stop-Process -Id $processId -ErrorAction Stop
    } else {
        & taskkill.exe /PID $processId /T /F | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Windows could not stop $Label (PID $processId). Try again from the same Windows account that started it."
        }
    }

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds(5)
    while ((Get-Process -Id $processId -ErrorAction SilentlyContinue) -and [DateTimeOffset]::UtcNow -lt $deadline) {
        Start-Sleep -Milliseconds 200
    }
    if (Get-Process -Id $processId -ErrorAction SilentlyContinue) {
        throw "$Label did not stop (PID $processId). Try again from the same Windows account that started it."
    }
    Write-Host "$Label stopped."
}

function Invoke-Setup {
    Write-Heading "SHOVE DEVELOPMENT SETUP"

    if ($ServerOnly -and $WithMobile) {
        throw "Choose either -ServerOnly or -WithMobile, not both."
    }

    $existing = $null
    if (Test-Path -LiteralPath $ConfigPath) { $existing = Read-Config }

    $defaultLocal = if ($null -ne $existing) { [string]$existing.localStorageRoot } else { Join-Path $ServerRoot ".shove-data" }
    $defaultExternal = if ($null -ne $existing) { [string]$existing.externalStorageRoot } else { "" }
    $defaultExternalName = if ($null -ne $existing) { [string]$existing.externalStorageName } else { "External SSD" }
    $configuredPort = if ($ScriptBoundParameterNames -contains "ServerPort") {
        $ServerPort
    } elseif ($null -ne $existing) {
        [int]$existing.serverPort
    } else {
        $ServerPort
    }

    if ([string]::IsNullOrWhiteSpace($LocalStorageRoot)) {
        $LocalStorageRoot = if ($NonInteractive) { $defaultLocal } else { Read-WithDefault "Local storage folder" $defaultLocal }
    }
    if ($ScriptBoundParameterNames -notcontains "ExternalStorageRoot") {
        $ExternalStorageRoot = if ($NonInteractive) { $defaultExternal } else { Read-Optional "Optional external storage folder (or 'none')" $defaultExternal }
    }
    if ([string]::IsNullOrWhiteSpace($ExternalStorageName)) {
        $ExternalStorageName = if ($NonInteractive) { $defaultExternalName } else { Read-WithDefault "External storage display name" $defaultExternalName }
    }

    $resolvedLocal = Resolve-ConfiguredPath $LocalStorageRoot
    $resolvedExternal = if ([string]::IsNullOrWhiteSpace($ExternalStorageRoot)) { "" } else { Resolve-ConfiguredPath $ExternalStorageRoot }

    if ($resolvedExternal -and $resolvedExternal -eq $resolvedLocal) {
        throw "Local and external storage folders must be different."
    }

    Ensure-ConfiguredDirectory $resolvedLocal "Local storage"
    if ($resolvedExternal) { Ensure-ConfiguredDirectory $resolvedExternal "External storage" }

    $startMobile = if ($WithMobile) {
        $true
    } elseif ($ServerOnly) {
        -not $ServerOnly
    } elseif ($null -ne $existing) {
        [bool]$existing.startMobile
    } else {
        $true
    }

    $config = [ordered]@{
        version = 1
        serverPort = $configuredPort
        localStorageRoot = $resolvedLocal
        externalStorageRoot = $resolvedExternal
        externalStorageName = $ExternalStorageName.Trim()
        startMobile = $startMobile
    }
    Save-Json $ConfigPath $config

    Write-Info "Configuration" $ConfigPath
    Write-Info "Local storage" $resolvedLocal
    Write-Info "External storage" $(if ($resolvedExternal) { $resolvedExternal } else { "not configured" })
    Write-Info "Mobile client" $(if ($config.startMobile) { "start with Shove" } else { "server only" })

    Assert-Prerequisites ([bool]$config.startMobile)
    Write-Host "Prerequisite commands are available." -ForegroundColor Green

    $configureFirewallNow = if ($ConfigureFirewall) {
        $true
    } elseif ($NonInteractive) {
        $false
    } else {
        Read-YesNo "Configure Windows Firewall for this home network now?" $true
    }
    if ($configureFirewallNow) {
        Invoke-Firewall
    } else {
        Write-Warning "Firewall setup was skipped. The phone may not reach Java or Expo until you run: .\shove.cmd firewall"
    }

    if (-not $SkipInstall) {
        Invoke-MavenPackage
        if ($config.startMobile) {
            $pnpm = Get-PnpmCommand
            Write-Host "Installing locked mobile dependencies..." -ForegroundColor Cyan
            Push-Location $RepositoryRoot
            try {
                & $pnpm "install" "--frozen-lockfile"
                if ($LASTEXITCODE -ne 0) { throw "pnpm install failed with exit code $LASTEXITCODE." }
            } finally {
                Pop-Location
            }
        }
    }

    Write-Host ""
    Write-Host "Setup complete. Start Shove with: .\shove.cmd start" -ForegroundColor Green
}

function Invoke-Start {
    $config = Read-Config
    Assert-Prerequisites ([bool]$config.startMobile)
    Repair-DuplicateProcessEnvironment
    [void](New-Item -ItemType Directory -Path $LogRoot -Force)

    if (Test-Path -LiteralPath $RuntimePath) {
        $oldRuntime = Get-Content -LiteralPath $RuntimePath -Raw | ConvertFrom-Json
        if ((Test-RecordedProcess $oldRuntime.server) -or (Test-RecordedProcess $oldRuntime.mobile)) {
            Write-Host "Shove is already running."
            Invoke-Status
            Open-ControlPanel ([int]$config.serverPort)
            return
        }
        Remove-Item -LiteralPath $RuntimePath -Force
    }

    if (Test-TcpPort ([int]$config.serverPort)) {
        throw "Port $($config.serverPort) is already in use by a process not managed by Shove."
    }
    if ([bool]$config.startMobile -and (Test-TcpPort 8081)) {
        throw "Port 8081 is already in use. Stop the other Metro process or configure server-only mode."
    }

    if (Test-ServerBuildRequired) { Invoke-MavenPackage }
    $jar = Get-ServerJar
    if ($null -eq $jar) { throw "The packaged Java server was not found after the build." }

    Write-Heading "STARTING SHOVE"
    $java = Get-RequiredCommand "java.exe" "Install a Java 21 JDK."
    $serverOut = Join-Path $LogRoot "server.out.log"
    $serverErr = Join-Path $LogRoot "server.error.log"

    $savedEnvironment = @{}
    $serverEnvironment = [ordered]@{
        SHOVE_PORT = [string]$config.serverPort
        SHOVE_DB_PATH = (Join-Path $ServerRoot "shove.db")
        SHOVE_STORAGE_ROOT = [string]$config.localStorageRoot
        SHOVE_EXTERNAL_STORAGE_ROOT = [string]$config.externalStorageRoot
        SHOVE_EXTERNAL_STORAGE_NAME = [string]$config.externalStorageName
    }
    foreach ($entry in $serverEnvironment.GetEnumerator()) {
        $savedEnvironment[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, "Process")
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, "Process")
    }

    try {
        $serverProcess = Start-Process -FilePath $java `
            -ArgumentList "-jar `"$($jar.FullName)`" --spring.profiles.active=dev" `
            -WorkingDirectory $ServerRoot `
            -RedirectStandardOutput $serverOut `
            -RedirectStandardError $serverErr `
            -WindowStyle Hidden `
            -PassThru
    } finally {
        foreach ($entry in $savedEnvironment.GetEnumerator()) {
            [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, "Process")
        }
    }

    $runtime = [ordered]@{
        version = 1
        server = [ordered]@{
            kind = "java"
            pid = $serverProcess.Id
            startedAtUtc = $serverProcess.StartTime.ToUniversalTime().ToString("o")
        }
        mobile = $null
        startedAtUtc = [DateTimeOffset]::UtcNow.ToString("o")
    }
    Save-Json $RuntimePath $runtime

    $health = Wait-ForServer ([int]$config.serverPort)
    if ($null -eq $health) {
        Stop-RecordedProcess ([pscustomobject]$runtime.server) "Java server"
        Remove-Item -LiteralPath $RuntimePath -Force -ErrorAction SilentlyContinue
        throw "The Java server did not become healthy. Read $serverErr and $serverOut."
    }
    Write-Host "Java server is healthy." -ForegroundColor Green

    if ([bool]$config.startMobile) {
        $pnpm = Get-PnpmCommand
        $node = Get-NodeCommand
        $mobileOut = Join-Path $LogRoot "mobile.out.log"
        $mobileErr = Join-Path $LogRoot "mobile.error.log"
        $savedPath = [Environment]::GetEnvironmentVariable("Path", "Process")
        $savedPhoneServerUrl = [Environment]::GetEnvironmentVariable("EXPO_PUBLIC_SHOVE_SERVER_URL", "Process")
        $nodeDirectory = Split-Path -Parent $node
        if (($savedPath -split ";") -notcontains $nodeDirectory) {
            [Environment]::SetEnvironmentVariable("Path", "$nodeDirectory;$savedPath", "Process")
        }
        $lanAddress = Get-LanAddress
        if ($lanAddress) {
            [Environment]::SetEnvironmentVariable(
                "EXPO_PUBLIC_SHOVE_SERVER_URL",
                "http://${lanAddress}:$($config.serverPort)",
                "Process")
        }
        try {
            $mobileProcess = Start-Process -FilePath $pnpm `
                -ArgumentList "--dir `"$MobileRoot`" start:lan" `
                -WorkingDirectory $RepositoryRoot `
                -RedirectStandardOutput $mobileOut `
                -RedirectStandardError $mobileErr `
                -WindowStyle Hidden `
                -PassThru
        } finally {
            [Environment]::SetEnvironmentVariable("Path", $savedPath, "Process")
            [Environment]::SetEnvironmentVariable("EXPO_PUBLIC_SHOVE_SERVER_URL", $savedPhoneServerUrl, "Process")
        }

        $runtime.mobile = [ordered]@{
            kind = "process-tree"
            pid = $mobileProcess.Id
            startedAtUtc = $mobileProcess.StartTime.ToUniversalTime().ToString("o")
        }
        Save-Json $RuntimePath $runtime

        $deadline = [DateTimeOffset]::UtcNow.AddSeconds(30)
        while (-not (Test-TcpPort 8081) -and [DateTimeOffset]::UtcNow -lt $deadline) {
            if (-not (Test-RecordedProcess ([pscustomobject]$runtime.mobile))) { break }
            Start-Sleep -Milliseconds 400
        }
        if (Test-TcpPort 8081) { Start-Sleep -Seconds 3 }
        if (-not (Test-TcpPort 8081)) {
            Stop-RecordedProcess ([pscustomobject]$runtime.mobile) "Expo/Metro"
            Stop-RecordedProcess ([pscustomobject]$runtime.server) "Java server"
            Remove-Item -LiteralPath $RuntimePath -Force -ErrorAction SilentlyContinue
            throw "Expo/Metro did not start. Read $mobileErr and $mobileOut."
        }
        Write-Host "Expo/Metro is ready." -ForegroundColor Green
    }

    Invoke-Status
    Write-Host ""
    Write-Host "The Shove control panel is ready." -ForegroundColor Green
    Open-ControlPanel ([int]$config.serverPort)
}

function Open-ControlPanel([int]$Port) {
    if ($NoOpen) { return }
    $url = "http://127.0.0.1:$Port/admin/"
    try {
        Start-Process -FilePath "explorer.exe" -ArgumentList $url
    } catch {
        Write-Warning "The control panel could not open automatically. Open $url in a browser."
    }
}

function Invoke-Status {
    $config = Read-Config
    Write-Heading "SHOVE STATUS"

    $runtime = $null
    if (Test-Path -LiteralPath $RuntimePath) {
        $runtime = Get-Content -LiteralPath $RuntimePath -Raw | ConvertFrom-Json
    }

    $health = Get-Health ([int]$config.serverPort)
    $serverRunning = $null -ne $health -and $health.status -eq "ok"
    $mobileRunning = Test-TcpPort 8081
    $lanAddress = Get-LanAddress

    Write-Info "Java server" $(if ($serverRunning) { "healthy" } else { "stopped/unreachable" })
    Write-Info "Expo/Metro" $(if ([bool]$config.startMobile) { if ($mobileRunning) { "ready" } else { "stopped/unreachable" } } else { "disabled" })
    Write-Info "Local storage" $(if (Test-Path -LiteralPath ([string]$config.localStorageRoot)) { [string]$config.localStorageRoot } else { "unavailable: $($config.localStorageRoot)" })
    if ([string]$config.externalStorageRoot) {
        Write-Info "External storage" $(if (Test-Path -LiteralPath ([string]$config.externalStorageRoot)) { [string]$config.externalStorageRoot } else { "disconnected: $($config.externalStorageRoot)" })
    } else {
        Write-Info "External storage" "not configured"
    }

    if ($lanAddress) {
        Write-Info "Phone server URL" "http://${lanAddress}:$($config.serverPort)"
        if ([bool]$config.startMobile) { Write-Info "Expo project URL" "exp://${lanAddress}:8081" }
    } else {
        Write-Warning "No private LAN IPv4 address was detected. Check the Windows Wi-Fi connection."
    }
    Write-Info "Control panel" "http://127.0.0.1:$($config.serverPort)/admin/"
    Write-Info "Logs" $LogRoot
}

function Invoke-Pair {
    $config = Read-Config
    $health = Get-Health ([int]$config.serverPort)
    if ($null -eq $health -or $health.status -ne "ok") {
        throw "The Shove server is not running. Run: .\shove.cmd start"
    }
    & $PairingScript -ServerUrl "http://127.0.0.1:$($config.serverPort)" -Cli:$Cli
}

function Invoke-Stop {
    Write-Heading "STOPPING SHOVE"
    if (-not (Test-Path -LiteralPath $RuntimePath)) {
        Write-Host "No managed Shove processes are recorded."
        return
    }

    $runtime = Get-Content -LiteralPath $RuntimePath -Raw | ConvertFrom-Json
    if ($null -ne $runtime.mobile) { Stop-RecordedProcess $runtime.mobile "Expo/Metro" }
    if ($null -ne $runtime.server) { Stop-RecordedProcess $runtime.server "Java server" }
    Remove-Item -LiteralPath $RuntimePath -Force
    Write-Host "Shove is stopped. Configuration, audit history, and originals were preserved." -ForegroundColor Green
}

function Show-Help {
    Write-Host @"

Shove developer launcher

  .\shove.cmd setup     Save storage settings, check prerequisites, and prepare dependencies
  .\shove.cmd start     Package when needed, then start Java and Expo/Metro
  .\shove.cmd status    Show health, storage, phone URLs, and log location
  .\shove.cmd pair      Open a fresh two-minute pairing-code popup
  .\shove.cmd pair -Cli Print the pairing code in this terminal
  .\shove.cmd firewall  Ask for UAC permission and configure home-subnet rules
  .\shove.cmd firewall -Remove
                        Remove only the Shove-managed firewall rules
  .\shove.cmd stop      Stop only the Java and Metro processes started by this launcher

Useful setup options:

  -LocalStorageRoot <path>
  -ExternalStorageRoot <path>
  -ExternalStorageName <name>
  -ServerOnly
  -WithMobile
  -NonInteractive
  -SkipInstall
  -ConfigureFirewall
  -NoOpen               Do not open the local control panel after start

This is development automation. It does not silently install system software or change Windows Firewall.
Firewall changes require an explicit setup choice and a Windows UAC approval.
"@
}

try {
    switch ($Command) {
        "setup" { Invoke-Setup }
        "start" { Invoke-Start }
        "status" { Invoke-Status }
        "pair" { Invoke-Pair }
        "firewall" { Invoke-Firewall ([bool]$Remove) }
        "stop" { Invoke-Stop }
        default { Show-Help }
    }
} catch {
    Write-Host ""
    Write-Host "Shove could not complete '$Command'." -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    if ($_.ScriptStackTrace) { Write-Host $_.ScriptStackTrace -ForegroundColor DarkGray }
    Write-Host ""
    Write-Host "Run '.\shove.cmd status' for current state or see docs\windows-usage-guide.md."
    exit 1
}
