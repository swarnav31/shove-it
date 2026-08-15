[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("Install", "Remove")]
    [string]$Action,

    [string]$Subnet,
    [int]$ServerPort = 8787,
    [string]$JavaProgram,
    [switch]$IncludeExpo,
    [string]$NodeProgram,
    [string]$LogPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$JavaRuleName = "ShoveIt-Java-8787"
$ExpoRuleName = "ShoveIt-Expo-8081"

function Write-FirewallLog([string]$Message) {
    if ([string]::IsNullOrWhiteSpace($LogPath)) { return }
    $parent = Split-Path -Parent $LogPath
    if ($parent) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
    $timestamp = [DateTimeOffset]::Now.ToString("o")
    Add-Content -LiteralPath $LogPath -Value "$timestamp $Message" -Encoding UTF8
}

function Write-RuleLog([string]$Name) {
    $rule = Get-NetFirewallRule -Name $Name -ErrorAction Stop
    $port = $rule | Get-NetFirewallPortFilter
    $address = $rule | Get-NetFirewallAddressFilter
    $program = $rule | Get-NetFirewallApplicationFilter
    Write-FirewallLog (
        "rule name={0}; enabled={1}; direction={2}; action={3}; profile={4}; protocol={5}; localPort={6}; remoteAddress={7}; program={8}" -f
        $rule.Name,
        $rule.Enabled,
        $rule.Direction,
        $rule.Action,
        $rule.Profile,
        $port.Protocol,
        ($port.LocalPort -join ","),
        ($address.RemoteAddress -join ","),
        $program.Program)
}

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "Firewall configuration must run with administrator permission."
}

function Remove-ShoveRule([string]$Name) {
    $existing = Get-NetFirewallRule -Name $Name -ErrorAction SilentlyContinue
    if ($null -ne $existing) {
        $existing | Remove-NetFirewallRule
    }
}

if ($Action -eq "Remove") {
    Write-FirewallLog "action=remove; requestedRules=$JavaRuleName,$ExpoRuleName"
    Remove-ShoveRule $JavaRuleName
    Remove-ShoveRule $ExpoRuleName
    Write-FirewallLog "action=remove; result=success"
    exit 0
}

if ([string]::IsNullOrWhiteSpace($Subnet)) {
    throw "A private LAN subnet is required."
}
if ([string]::IsNullOrWhiteSpace($JavaProgram) -or -not (Test-Path -LiteralPath $JavaProgram -PathType Leaf)) {
    throw "The Java program path is missing or invalid: $JavaProgram"
}
if ($IncludeExpo -and ([string]::IsNullOrWhiteSpace($NodeProgram) -or -not (Test-Path -LiteralPath $NodeProgram -PathType Leaf))) {
    throw "The Node.js program path is missing or invalid: $NodeProgram"
}

Write-FirewallLog "action=install; subnet=$Subnet; serverPort=$ServerPort; javaProgram=$JavaProgram; includeExpo=$IncludeExpo; nodeProgram=$NodeProgram"

Remove-ShoveRule $JavaRuleName
New-NetFirewallRule `
    -Name $JavaRuleName `
    -DisplayName "Shove-It Java server (home LAN only)" `
    -Description "Allows paired devices on $Subnet to reach the Shove server." `
    -Direction Inbound `
    -Action Allow `
    -Protocol TCP `
    -LocalPort $ServerPort `
    -RemoteAddress $Subnet `
    -Program $JavaProgram `
    -Profile Public,Private `
    -Enabled True | Out-Null
Write-RuleLog $JavaRuleName

if ($IncludeExpo) {
    Remove-ShoveRule $ExpoRuleName
    New-NetFirewallRule `
        -Name $ExpoRuleName `
        -DisplayName "Shove-It Expo development server (home LAN only)" `
        -Description "Allows Expo Go on $Subnet to load the Shove development client." `
        -Direction Inbound `
        -Action Allow `
        -Protocol TCP `
        -LocalPort 8081 `
        -RemoteAddress $Subnet `
        -Program $NodeProgram `
        -Profile Public,Private `
        -Enabled True | Out-Null
    Write-RuleLog $ExpoRuleName
} else {
    Remove-ShoveRule $ExpoRuleName
}
Write-FirewallLog "action=install; result=success"
