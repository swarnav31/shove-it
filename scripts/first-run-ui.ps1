[CmdletBinding()]
param(
    [switch]$CheckOnly,
    [switch]$ForceSetup
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepositoryRoot = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$LauncherPath = Join-Path $RepositoryRoot "shove.ps1"
$StateRoot = Join-Path $RepositoryRoot ".shove-dev"
$ConfigPath = Join-Path $StateRoot "config.json"
$SetupOutLog = Join-Path $StateRoot "logs\first-run-setup.out.log"

function Find-CommandPath([string]$Name, [string[]]$Candidates = @()) {
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($null -ne $command) { return $command.Source }
    foreach ($candidate in $Candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) { return $candidate }
    }
    return $null
}

function Get-PrerequisiteState {
    $nodeCandidates = @(
        (Join-Path $env:LOCALAPPDATA "Programs\nodejs\node.exe"),
        (Join-Path $env:ProgramFiles "nodejs\node.exe")
    )
    $node = Find-CommandPath "node.exe" $nodeCandidates
    $pnpmCandidates = if ($node) { @((Join-Path (Split-Path -Parent $node) "pnpm.cmd")) } else { @() }
    return [ordered]@{
        Java = Find-CommandPath "java.exe"
        Maven = Find-CommandPath "mvn.cmd"
        Node = $node
        Pnpm = Find-CommandPath "pnpm.cmd" $pnpmCandidates
    }
}

function Get-ExternalDriveCandidates {
    $systemRoot = [IO.Path]::GetPathRoot($env:SystemRoot)
    $repositoryDrive = [IO.Path]::GetPathRoot($RepositoryRoot)
    return [IO.DriveInfo]::GetDrives() |
        Where-Object {
            $_.IsReady -and
            $_.Name -ne $systemRoot -and
            $_.Name -ne $repositoryDrive -and
            $_.DriveType -in @([IO.DriveType]::Fixed, [IO.DriveType]::Removable)
        } |
        ForEach-Object {
            $volume = if ($_.VolumeLabel) { $_.VolumeLabel } else { "External drive" }
            [pscustomobject]@{
                Root = $_.RootDirectory.FullName
                Label = "$volume ($($_.Name.TrimEnd('\')))"
                SuggestedPath = Join-Path $_.RootDirectory.FullName "Shove"
            }
        }
}

function Escape-SingleQuoted([string]$Value) {
    return $Value.Replace("'", "''")
}

function New-EncodedCommand([string]$Command) {
    return [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($Command))
}

function Read-ExistingConfig {
    if (-not (Test-Path -LiteralPath $ConfigPath)) { return $null }
    try {
        return Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
    } catch {
        return $null
    }
}

$Prerequisites = Get-PrerequisiteState
$MissingPrerequisites = @($Prerequisites.GetEnumerator() | Where-Object { -not $_.Value } | ForEach-Object Key)
$ExternalDrives = @(Get-ExternalDriveCandidates)
$ExistingConfig = Read-ExistingConfig

if ($CheckOnly) {
    [pscustomobject]@{
        repositoryRoot = $RepositoryRoot
        configured = $null -ne $ExistingConfig
        prerequisitesReady = $MissingPrerequisites.Count -eq 0
        missingPrerequisites = $MissingPrerequisites
        externalDrives = @($ExternalDrives | ForEach-Object Label)
    } | ConvertTo-Json -Depth 4
    exit 0
}

if ($ExistingConfig -and -not $ForceSetup) {
    $command = "& '$(Escape-SingleQuoted $LauncherPath)' start"
    $encoded = New-EncodedCommand $command
    Start-Process -FilePath "powershell.exe" `
        -ArgumentList "-NoProfile -ExecutionPolicy Bypass -EncodedCommand $encoded" `
        -WorkingDirectory $RepositoryRoot `
        -WindowStyle Hidden
    exit 0
}

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

$Colors = @{
    Background = [Drawing.Color]::FromArgb(7, 17, 14)
    Panel = [Drawing.Color]::FromArgb(12, 27, 22)
    PanelStrong = [Drawing.Color]::FromArgb(16, 35, 28)
    Text = [Drawing.Color]::FromArgb(243, 255, 248)
    Muted = [Drawing.Color]::FromArgb(156, 180, 168)
    Mint = [Drawing.Color]::FromArgb(101, 230, 173)
    Red = [Drawing.Color]::FromArgb(255, 154, 154)
    Amber = [Drawing.Color]::FromArgb(248, 200, 107)
}

function New-Label([string]$Text, [int]$X, [int]$Y, [int]$Width, [int]$Height, [float]$Size = 9, [bool]$Bold = $false) {
    $label = New-Object Windows.Forms.Label
    $label.Text = $Text
    $label.Location = New-Object Drawing.Point($X, $Y)
    $label.Size = New-Object Drawing.Size($Width, $Height)
    $style = if ($Bold) { [Drawing.FontStyle]::Bold } else { [Drawing.FontStyle]::Regular }
    $label.Font = New-Object Drawing.Font("Segoe UI", $Size, $style)
    $label.ForeColor = $Colors.Text
    return $label
}

function Style-TextBox([Windows.Forms.TextBox]$Control) {
    $Control.BackColor = $Colors.PanelStrong
    $Control.ForeColor = $Colors.Text
    $Control.BorderStyle = [Windows.Forms.BorderStyle]::FixedSingle
    $Control.Font = New-Object Drawing.Font("Segoe UI", 10)
}

function Style-Button([Windows.Forms.Button]$Control, [bool]$Primary = $false) {
    $Control.FlatStyle = [Windows.Forms.FlatStyle]::Flat
    $Control.FlatAppearance.BorderSize = if ($Primary) { 0 } else { 1 }
    $Control.FlatAppearance.BorderColor = [Drawing.Color]::FromArgb(54, 82, 69)
    $Control.BackColor = if ($Primary) { $Colors.Mint } else { $Colors.PanelStrong }
    $Control.ForeColor = if ($Primary) { $Colors.Background } else { $Colors.Text }
    $Control.Font = New-Object Drawing.Font("Segoe UI", 9.5, [Drawing.FontStyle]::Bold)
    $Control.Cursor = [Windows.Forms.Cursors]::Hand
}

function Add-FolderPicker([Windows.Forms.TextBox]$Target, [string]$Description) {
    $dialog = New-Object Windows.Forms.FolderBrowserDialog
    $dialog.Description = $Description
    $dialog.ShowNewFolderButton = $true
    if ($Target.Text -and (Test-Path -LiteralPath $Target.Text)) { $dialog.SelectedPath = $Target.Text }
    if ($dialog.ShowDialog() -eq [Windows.Forms.DialogResult]::OK) { $Target.Text = $dialog.SelectedPath }
    $dialog.Dispose()
}

$form = New-Object Windows.Forms.Form
$form.Text = if ($ExistingConfig) { "Shove settings" } else { "Set up Shove" }
$form.ClientSize = New-Object Drawing.Size(760, 720)
$form.MinimumSize = New-Object Drawing.Size(776, 759)
$form.StartPosition = [Windows.Forms.FormStartPosition]::CenterScreen
$form.BackColor = $Colors.Background
$form.ForeColor = $Colors.Text
$form.Font = New-Object Drawing.Font("Segoe UI", 9)
$form.Icon = [Drawing.SystemIcons]::Application

$brand = New-Label "S" 34 27 46 46 20 $true
$brand.TextAlign = [Drawing.ContentAlignment]::MiddleCenter
$brand.BackColor = $Colors.Mint
$brand.ForeColor = $Colors.Background
$form.Controls.Add($brand)
$form.Controls.Add((New-Label "Shove" 94 25 250 32 18 $true))
$form.Controls.Add((New-Label "Send your originals home." 95 57 360 22 9 $false))

$title = New-Label $(if ($ExistingConfig) { "Storage and connection" } else { "Let's get your PC ready." }) 34 104 690 44 25 $true
$form.Controls.Add($title)
$intro = New-Label "Choose where originals should land. Shove will prepare its private server and ask before changing Windows Firewall." 36 151 680 42 10 $false
$intro.ForeColor = $Colors.Muted
$form.Controls.Add($intro)

$storagePanel = New-Object Windows.Forms.Panel
$storagePanel.Location = New-Object Drawing.Point(34, 207)
$storagePanel.Size = New-Object Drawing.Size(692, 238)
$storagePanel.BackColor = $Colors.Panel
$form.Controls.Add($storagePanel)
$storagePanel.Controls.Add((New-Label "STORAGE" 20 16 200 18 8 $true))

$localLabel = New-Label "Windows folder" 20 46 170 20 9 $true
$storagePanel.Controls.Add($localLabel)
$localText = New-Object Windows.Forms.TextBox
$localText.Location = New-Object Drawing.Point(20, 70)
$localText.Size = New-Object Drawing.Size(550, 31)
$localText.Text = if ($ExistingConfig) { [string]$ExistingConfig.localStorageRoot } else { Join-Path $RepositoryRoot "server\.shove-data" }
Style-TextBox $localText
$storagePanel.Controls.Add($localText)
$localBrowse = New-Object Windows.Forms.Button
$localBrowse.Text = "Browse"
$localBrowse.Location = New-Object Drawing.Point(581, 68)
$localBrowse.Size = New-Object Drawing.Size(88, 33)
Style-Button $localBrowse
$localBrowse.Add_Click({ Add-FolderPicker $localText "Choose the Windows folder for Shove originals" })
$storagePanel.Controls.Add($localBrowse)

$externalLabel = New-Label "External SSD (optional)" 20 119 200 20 9 $true
$storagePanel.Controls.Add($externalLabel)
$externalText = New-Object Windows.Forms.TextBox
$externalText.Location = New-Object Drawing.Point(20, 143)
$externalText.Size = New-Object Drawing.Size(550, 31)
$externalText.Text = if ($ExistingConfig) { [string]$ExistingConfig.externalStorageRoot } elseif ($ExternalDrives.Count) { [string]$ExternalDrives[0].SuggestedPath } else { "" }
Style-TextBox $externalText
$storagePanel.Controls.Add($externalText)
$externalBrowse = New-Object Windows.Forms.Button
$externalBrowse.Text = "Browse"
$externalBrowse.Location = New-Object Drawing.Point(581, 141)
$externalBrowse.Size = New-Object Drawing.Size(88, 33)
Style-Button $externalBrowse
$externalBrowse.Add_Click({ Add-FolderPicker $externalText "Choose a folder on the external SSD, or cancel to use Windows only" })
$storagePanel.Controls.Add($externalBrowse)

$nameLabel = New-Label "Name shown on phone" 20 190 180 20 9 $true
$storagePanel.Controls.Add($nameLabel)
$externalName = New-Object Windows.Forms.TextBox
$externalName.Location = New-Object Drawing.Point(203, 187)
$externalName.Size = New-Object Drawing.Size(367, 31)
$externalName.Text = if ($ExistingConfig) { [string]$ExistingConfig.externalStorageName } elseif ($ExternalDrives.Count) { [string]$ExternalDrives[0].Label } else { "External SSD" }
Style-TextBox $externalName
$storagePanel.Controls.Add($externalName)

$connectionPanel = New-Object Windows.Forms.Panel
$connectionPanel.Location = New-Object Drawing.Point(34, 459)
$connectionPanel.Size = New-Object Drawing.Size(692, 82)
$connectionPanel.BackColor = $Colors.Panel
$form.Controls.Add($connectionPanel)
$firewallCheck = New-Object Windows.Forms.CheckBox
$firewallCheck.Text = "Allow my phone to reach Shove on this home Wi-Fi"
$firewallCheck.Checked = $true
$firewallCheck.Location = New-Object Drawing.Point(20, 15)
$firewallCheck.Size = New-Object Drawing.Size(620, 24)
$firewallCheck.ForeColor = $Colors.Text
$firewallCheck.Font = New-Object Drawing.Font("Segoe UI", 9.5, [Drawing.FontStyle]::Bold)
$connectionPanel.Controls.Add($firewallCheck)
$firewallHelp = New-Label "Windows will show one permission prompt. Access is limited to Shove's two ports and your current home network." 42 43 620 30 8.5 $false
$firewallHelp.ForeColor = $Colors.Muted
$connectionPanel.Controls.Add($firewallHelp)

$readiness = New-Label "" 36 555 690 24 9 $true
$form.Controls.Add($readiness)
$progress = New-Object Windows.Forms.ProgressBar
$progress.Location = New-Object Drawing.Point(36, 586)
$progress.Size = New-Object Drawing.Size(690, 8)
$progress.Style = [Windows.Forms.ProgressBarStyle]::Marquee
$progress.MarqueeAnimationSpeed = 22
$progress.Visible = $false
$form.Controls.Add($progress)

$logBox = New-Object Windows.Forms.TextBox
$logBox.Location = New-Object Drawing.Point(36, 605)
$logBox.Size = New-Object Drawing.Size(690, 42)
$logBox.Multiline = $true
$logBox.ReadOnly = $true
$logBox.ScrollBars = [Windows.Forms.ScrollBars]::Vertical
$logBox.Visible = $false
Style-TextBox $logBox
$form.Controls.Add($logBox)

$prepareButton = New-Object Windows.Forms.Button
$prepareButton.Text = if ($ExistingConfig) { "Save and prepare" } else { "Prepare Shove" }
$prepareButton.Location = New-Object Drawing.Point(484, 660)
$prepareButton.Size = New-Object Drawing.Size(242, 43)
Style-Button $prepareButton $true
$form.Controls.Add($prepareButton)

$openButton = New-Object Windows.Forms.Button
$openButton.Text = "Open Shove"
$openButton.Location = New-Object Drawing.Point(484, 660)
$openButton.Size = New-Object Drawing.Size(242, 43)
$openButton.Visible = $false
Style-Button $openButton $true
$form.Controls.Add($openButton)

$cancelButton = New-Object Windows.Forms.Button
$cancelButton.Text = "Close"
$cancelButton.Location = New-Object Drawing.Point(36, 660)
$cancelButton.Size = New-Object Drawing.Size(100, 43)
Style-Button $cancelButton
$cancelButton.Add_Click({ $form.Close() })
$form.Controls.Add($cancelButton)

$setupProcess = $null
$timer = New-Object Windows.Forms.Timer
$timer.Interval = 350
$timer.Add_Tick({
    if ($null -eq $setupProcess) { return }
    $lines = @()
    if (Test-Path -LiteralPath $SetupOutLog) { $lines += Get-Content -LiteralPath $SetupOutLog -Tail 4 -ErrorAction SilentlyContinue }
    if ($lines.Count) {
        $logBox.Lines = $lines
        $logBox.SelectionStart = $logBox.TextLength
        $logBox.ScrollToCaret()
    }
    if (-not $setupProcess.HasExited) { return }

    $timer.Stop()
    $progress.Visible = $false
    $setupProcess.Refresh()
    $serverJarReady = @(Get-ChildItem -LiteralPath (Join-Path $RepositoryRoot "server\target") `
            -Filter "shove-it-server-*.jar" -File -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notlike "*.original" }).Count -gt 0
    $setupComplete = (Test-Path -LiteralPath $ConfigPath) -and
        $serverJarReady -and
        (Test-Path -LiteralPath (Join-Path $RepositoryRoot "apps\mobile\node_modules")) -and
        (Test-Path -LiteralPath $SetupOutLog) -and
        [bool](Select-String -LiteralPath $SetupOutLog -SimpleMatch "Setup complete." -Quiet)
    if ($setupProcess.ExitCode -eq 0 -or $setupComplete) {
        $readiness.Text = "Shove is ready. Open it and pair your phone."
        $readiness.ForeColor = $Colors.Mint
        $prepareButton.Visible = $false
        $openButton.Visible = $true
        $localText.Enabled = $false
        $externalText.Enabled = $false
        $externalName.Enabled = $false
        $localBrowse.Enabled = $false
        $externalBrowse.Enabled = $false
        $firewallCheck.Enabled = $false
    } else {
        $readiness.Text = "Setup needs attention. The details above explain what to fix."
        $readiness.ForeColor = $Colors.Red
        $prepareButton.Enabled = $true
        $prepareButton.Text = "Try again"
        $cancelButton.Enabled = $true
    }
})

if ($MissingPrerequisites.Count) {
    $readiness.Text = "This source preview still needs: $($MissingPrerequisites -join ', '). See the README prerequisites section."
    $readiness.ForeColor = $Colors.Amber
    $prepareButton.Enabled = $false
} else {
    $readiness.Text = if ($ExistingConfig) { "Settings loaded. Save them or open the existing setup." } else { "Ready to prepare. Your existing files will not be changed." }
    $readiness.ForeColor = $Colors.Mint
}

$prepareButton.Add_Click({
    if ([string]::IsNullOrWhiteSpace($localText.Text)) {
        [Windows.Forms.MessageBox]::Show("Choose a Windows storage folder.", "Shove", "OK", "Warning") | Out-Null
        return
    }
    if ($externalText.Text.Trim() -and $externalText.Text.Trim() -eq $localText.Text.Trim()) {
        [Windows.Forms.MessageBox]::Show("Windows and external storage must use different folders.", "Shove", "OK", "Warning") | Out-Null
        return
    }

    [void](New-Item -ItemType Directory -Path (Split-Path -Parent $SetupOutLog) -Force)
    Remove-Item -LiteralPath $SetupOutLog -Force -ErrorAction SilentlyContinue
    $setupCommand = "& '$(Escape-SingleQuoted $LauncherPath)' setup " +
        "-LocalStorageRoot '$(Escape-SingleQuoted $localText.Text.Trim())' " +
        "-ExternalStorageRoot '$(Escape-SingleQuoted $externalText.Text.Trim())' " +
        "-ExternalStorageName '$(Escape-SingleQuoted $externalName.Text.Trim())' " +
        "-NonInteractive" + $(if ($firewallCheck.Checked) { " -ConfigureFirewall" } else { "" })
    $command = "$setupCommand *> '$(Escape-SingleQuoted $SetupOutLog)'"
    $encoded = New-EncodedCommand $command
    $script:setupProcess = Start-Process -FilePath "powershell.exe" `
        -ArgumentList "-NoProfile -ExecutionPolicy Bypass -EncodedCommand $encoded" `
        -WorkingDirectory $RepositoryRoot `
        -WindowStyle Hidden `
        -PassThru
    $prepareButton.Enabled = $false
    $cancelButton.Enabled = $false
    $progress.Visible = $true
    $logBox.Visible = $true
    $readiness.Text = "Preparing the private server and mobile client…"
    $readiness.ForeColor = $Colors.Text
    $timer.Start()
})

$openButton.Add_Click({
    $command = "& '$(Escape-SingleQuoted $LauncherPath)' start"
    $encoded = New-EncodedCommand $command
    Start-Process -FilePath "powershell.exe" `
        -ArgumentList "-NoProfile -ExecutionPolicy Bypass -EncodedCommand $encoded" `
        -WorkingDirectory $RepositoryRoot `
        -WindowStyle Hidden
    $form.Close()
})

$form.Add_FormClosing({
    if ($null -ne $setupProcess -and -not $setupProcess.HasExited) {
        $_.Cancel = $true
        [Windows.Forms.MessageBox]::Show("Shove is still preparing. Please wait for it to finish.", "Shove", "OK", "Information") | Out-Null
    }
})
$form.Add_FormClosed({ $timer.Stop(); $timer.Dispose() })

[void]$form.ShowDialog()
