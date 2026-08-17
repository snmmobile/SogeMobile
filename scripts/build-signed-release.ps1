param(
    [string]$KeystorePath = "$env:USERPROFILE\Documents\apps\signing\sogemobile-release.jks",
    [string]$DestinationPath = "$env:USERPROFILE\Documents\apps\SogeMobile-v1.4.apk"
)

$ErrorActionPreference = 'Stop'
$keyAlias = 'sogemobile'
$projectRoot = Split-Path -Parent $PSScriptRoot
$androidSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$keystoreExists = Test-Path -LiteralPath $KeystorePath

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$form = New-Object System.Windows.Forms.Form
$form.Text = if ($keystoreExists) { 'Unlock SogeMobile release key' } else { 'Create SogeMobile release key' }
$form.Size = New-Object System.Drawing.Size(470, 235)
$form.StartPosition = 'CenterScreen'
$form.TopMost = $true
$form.FormBorderStyle = 'FixedDialog'
$form.MaximizeBox = $false
$form.MinimizeBox = $false

$message = New-Object System.Windows.Forms.Label
$message.Location = New-Object System.Drawing.Point(18, 18)
$message.Size = New-Object System.Drawing.Size(420, 42)
$message.Text = if ($keystoreExists) {
    'Enter the permanent release-key password. It is not saved by this script.'
} else {
    'Choose a strong permanent password and save it in your password manager. It is required for every future app update.'
}
$form.Controls.Add($message)

$passwordLabel = New-Object System.Windows.Forms.Label
$passwordLabel.Location = New-Object System.Drawing.Point(18, 72)
$passwordLabel.Size = New-Object System.Drawing.Size(100, 22)
$passwordLabel.Text = 'Password'
$form.Controls.Add($passwordLabel)

$passwordBox = New-Object System.Windows.Forms.TextBox
$passwordBox.Location = New-Object System.Drawing.Point(125, 69)
$passwordBox.Size = New-Object System.Drawing.Size(300, 24)
$passwordBox.UseSystemPasswordChar = $true
$form.Controls.Add($passwordBox)

$confirmLabel = New-Object System.Windows.Forms.Label
$confirmLabel.Location = New-Object System.Drawing.Point(18, 108)
$confirmLabel.Size = New-Object System.Drawing.Size(100, 22)
$confirmLabel.Text = 'Confirm'
$confirmLabel.Visible = -not $keystoreExists
$form.Controls.Add($confirmLabel)

$confirmBox = New-Object System.Windows.Forms.TextBox
$confirmBox.Location = New-Object System.Drawing.Point(125, 105)
$confirmBox.Size = New-Object System.Drawing.Size(300, 24)
$confirmBox.UseSystemPasswordChar = $true
$confirmBox.Visible = -not $keystoreExists
$form.Controls.Add($confirmBox)

$okButton = New-Object System.Windows.Forms.Button
$okButton.Location = New-Object System.Drawing.Point(269, 150)
$okButton.Size = New-Object System.Drawing.Size(75, 30)
$okButton.Text = 'Continue'
$okButton.Add_Click({
    if ($passwordBox.Text.Length -lt 12) {
        [System.Windows.Forms.MessageBox]::Show('Use at least 12 characters.', 'Password required') | Out-Null
        return
    }
    if (-not $keystoreExists -and $passwordBox.Text -cne $confirmBox.Text) {
        [System.Windows.Forms.MessageBox]::Show('The passwords do not match.', 'Check password') | Out-Null
        return
    }
    $form.DialogResult = [System.Windows.Forms.DialogResult]::OK
    $form.Close()
})
$form.Controls.Add($okButton)

$cancelButton = New-Object System.Windows.Forms.Button
$cancelButton.Location = New-Object System.Drawing.Point(350, 150)
$cancelButton.Size = New-Object System.Drawing.Size(75, 30)
$cancelButton.Text = 'Cancel'
$cancelButton.DialogResult = [System.Windows.Forms.DialogResult]::Cancel
$form.Controls.Add($cancelButton)
$form.AcceptButton = $okButton
$form.CancelButton = $cancelButton
$form.Add_Shown({ $passwordBox.Focus() })

if ($form.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) {
    throw 'Release signing was cancelled.'
}

$securePassword = ConvertTo-SecureString $passwordBox.Text -AsPlainText -Force
$passwordBox.Clear()
$confirmBox.Clear()
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)

try {
    $releasePassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    $env:SOGEMOBILE_KEYSTORE = $KeystorePath
    $env:SOGEMOBILE_STORE_PASSWORD = $releasePassword
    $env:SOGEMOBILE_KEY_ALIAS = $keyAlias
    $env:SOGEMOBILE_KEY_PASSWORD = $releasePassword
    $env:ANDROID_HOME = $androidSdk

    if (-not $keystoreExists) {
        $keystoreDirectory = Split-Path -Parent $KeystorePath
        New-Item -ItemType Directory -Force -Path $keystoreDirectory | Out-Null
        & keytool.exe -genkeypair -v -keystore $KeystorePath -alias $keyAlias `
            -keyalg RSA -keysize 4096 -validity 10000 `
            -dname 'CN=SogeMobile, OU=Mobile, O=SogeMobile, L=Port-au-Prince, ST=Ouest, C=HT' `
            -storepass:env SOGEMOBILE_STORE_PASSWORD -keypass:env SOGEMOBILE_KEY_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Release-key creation failed.' }
    }

    Push-Location $projectRoot
    try {
        & .\gradlew.bat testDebugUnitTest lintRelease assembleRelease
        if ($LASTEXITCODE -ne 0) { throw 'The signed release build failed.' }
    } finally {
        Pop-Location
    }

    $builtApk = Join-Path $projectRoot 'app\build\outputs\apk\release\sogemobile.apk'
    if (-not (Test-Path -LiteralPath $builtApk)) { throw "Signed APK not found at $builtApk" }
    Copy-Item -LiteralPath $builtApk -Destination $DestinationPath -Force

    $buildTools = Get-ChildItem -LiteralPath (Join-Path $androidSdk 'build-tools') -Directory |
        Sort-Object { [version]$_.Name } -Descending |
        Select-Object -First 1
    & (Join-Path $buildTools.FullName 'apksigner.bat') verify --verbose --print-certs $DestinationPath
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }

    Write-Output "Signed APK: $DestinationPath"
    Write-Output "Release keystore: $KeystorePath"
} finally {
    $releasePassword = $null
    $env:SOGEMOBILE_KEYSTORE = $null
    $env:SOGEMOBILE_STORE_PASSWORD = $null
    $env:SOGEMOBILE_KEY_ALIAS = $null
    $env:SOGEMOBILE_KEY_PASSWORD = $null
    if ($passwordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
}
