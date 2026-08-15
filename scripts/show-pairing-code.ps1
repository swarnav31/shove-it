[CmdletBinding()]
param(
    [switch]$Cli,
    [string]$ServerUrl = "http://127.0.0.1:8787"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$sessionUrl = $ServerUrl.TrimEnd('/') + "/api/v1/pairing/sessions"

try {
    $session = Invoke-RestMethod -Method Post -Uri $sessionUrl
} catch {
    Write-Error "Could not create a pairing code. Start the Shove Java server, then try again. $($_.Exception.Message)"
    exit 1
}

$code = [string]$session.code
$expiresAt = [DateTimeOffset]::Parse([string]$session.expiresAt)

Write-Host ""
Write-Host "SHOVE PAIRING CODE" -ForegroundColor Green
Write-Host ""
Write-Host "    $code" -ForegroundColor White
Write-Host ""
Write-Host "Single use. Expires at $($expiresAt.ToLocalTime().ToString('T')) (two-minute lifetime)."
Write-Host "Enter it in Step 2 on the phone."
Write-Host ""

if ($Cli) {
    exit 0
}

try {
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
} catch {
    Write-Warning "The Windows popup is unavailable; use the code printed above."
    exit 0
}

$form = New-Object System.Windows.Forms.Form
$form.Text = "Pair your phone with Shove"
$form.ClientSize = New-Object System.Drawing.Size(430, 265)
$form.StartPosition = "CenterScreen"
$form.FormBorderStyle = "FixedDialog"
$form.MaximizeBox = $false
$form.MinimizeBox = $false
$form.TopMost = $true
$form.BackColor = [System.Drawing.Color]::FromArgb(7, 17, 14)
$form.ForeColor = [System.Drawing.Color]::FromArgb(244, 255, 249)

$title = New-Object System.Windows.Forms.Label
$title.Text = "Pair your phone"
$title.Font = New-Object System.Drawing.Font("Segoe UI", 17, [System.Drawing.FontStyle]::Bold)
$title.Location = New-Object System.Drawing.Point(24, 20)
$title.Size = New-Object System.Drawing.Size(380, 34)
$form.Controls.Add($title)

$instruction = New-Object System.Windows.Forms.Label
$instruction.Text = "Enter this single-use code in Step 2 of the Shove app:"
$instruction.Font = New-Object System.Drawing.Font("Segoe UI", 9)
$instruction.ForeColor = [System.Drawing.Color]::FromArgb(169, 189, 180)
$instruction.Location = New-Object System.Drawing.Point(27, 62)
$instruction.Size = New-Object System.Drawing.Size(370, 22)
$form.Controls.Add($instruction)

$codeLabel = New-Object System.Windows.Forms.Label
$codeLabel.Text = $code.Insert(3, " ")
$codeLabel.Font = New-Object System.Drawing.Font("Consolas", 30, [System.Drawing.FontStyle]::Bold)
$codeLabel.ForeColor = [System.Drawing.Color]::FromArgb(101, 230, 173)
$codeLabel.TextAlign = "MiddleCenter"
$codeLabel.Location = New-Object System.Drawing.Point(25, 84)
$codeLabel.Size = New-Object System.Drawing.Size(380, 58)
$form.Controls.Add($codeLabel)

$countdown = New-Object System.Windows.Forms.Label
$countdown.Font = New-Object System.Drawing.Font("Segoe UI", 10)
$countdown.TextAlign = "MiddleCenter"
$countdown.Location = New-Object System.Drawing.Point(25, 142)
$countdown.Size = New-Object System.Drawing.Size(380, 28)
$form.Controls.Add($countdown)

$copyButton = New-Object System.Windows.Forms.Button
$copyButton.Text = "Copy code"
$copyButton.FlatStyle = "Flat"
$copyButton.BackColor = [System.Drawing.Color]::FromArgb(101, 230, 173)
$copyButton.ForeColor = [System.Drawing.Color]::FromArgb(8, 19, 15)
$copyButton.Font = New-Object System.Drawing.Font("Segoe UI", 10, [System.Drawing.FontStyle]::Bold)
$copyButton.Location = New-Object System.Drawing.Point(87, 188)
$copyButton.Size = New-Object System.Drawing.Size(120, 42)
$form.Controls.Add($copyButton)

$closeButton = New-Object System.Windows.Forms.Button
$closeButton.Text = "Close"
$closeButton.FlatStyle = "Flat"
$closeButton.BackColor = [System.Drawing.Color]::FromArgb(16, 34, 27)
$closeButton.ForeColor = [System.Drawing.Color]::FromArgb(244, 255, 249)
$closeButton.Location = New-Object System.Drawing.Point(223, 188)
$closeButton.Size = New-Object System.Drawing.Size(120, 42)
$form.Controls.Add($closeButton)

$uiState = @{
    Code = $code
    ExpiresAt = $expiresAt
}

$copyButton.Add_Click({
    [System.Windows.Forms.Clipboard]::SetText($uiState.Code)
    $copyButton.Text = "Copied"
})
$closeButton.Add_Click({ $form.Close() })

$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 250
$timer.Add_Tick({
    $remaining = $uiState.ExpiresAt - [DateTimeOffset]::UtcNow
    if ($remaining.TotalSeconds -le 0) {
        $countdown.Text = "Expired - close this window and run pair.cmd again."
        $countdown.ForeColor = [System.Drawing.Color]::FromArgb(255, 154, 154)
        $copyButton.Enabled = $false
        $timer.Stop()
        return
    }

    $seconds = [Math]::Ceiling($remaining.TotalSeconds)
    $countdown.Text = "Expires in {0}:{1:00}" -f [Math]::Floor($seconds / 60), ($seconds % 60)
    $countdown.ForeColor = [System.Drawing.Color]::FromArgb(169, 189, 180)
})

$form.Add_Shown({ $timer.Start() })
$form.Add_FormClosed({ $timer.Stop(); $timer.Dispose() })
[void]$form.ShowDialog()
