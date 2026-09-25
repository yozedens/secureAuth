<#
.SYNOPSIS
  在 Windows 上安装 SecureAuth 所需的 Android SDK（不需要 Android Studio）。

.DESCRIPTION
  依次完成：检查 / 安装 JDK 17 与 Git → 下载 Android 命令行工具 → 安装 SDK 组件
  （platform-tools、Android 15 平台、build-tools、模拟器与系统镜像）→ 设置用户环境变量
  → 生成 local.properties → 创建 API 35 与 API 26 两台模拟器 → 检查模拟器硬件加速。
  可以重复运行：已完成的步骤会跳过。

  用法（在项目根目录）：
    powershell -ExecutionPolicy Bypass -File scripts\setup-android-sdk.ps1
    powershell -ExecutionPolicy Bypass -File scripts\setup-android-sdk.ps1 -AcceptLicenses

  不修改系统级设置、不需要管理员权限；需要管理员的步骤（如开启 Windows 虚拟机监控程序平台）
  只打印说明，由用户自己执行。

.PARAMETER AcceptLicenses
  代表用户同意 Android SDK 许可协议（以及 winget 安装 JDK / Git 时的协议）并自动接受。
  只有在用户明确同意后才可以加这个参数。不加时，如果许可尚未接受，脚本会停下并说明如何处理。

.PARAMETER SdkRoot
  SDK 安装目录，默认 %LOCALAPPDATA%\Android\Sdk（与 Android Studio 相同）。

.PARAMETER SkipEmulator
  不安装模拟器与系统镜像（节省约 4 GB），只装编译所需组件。

.PARAMETER ProxyHost
.PARAMETER ProxyPort
  可选 HTTP 代理，例如 -ProxyHost 127.0.0.1 -ProxyPort 7890。
#>
[CmdletBinding()]
param(
    [switch]$AcceptLicenses,
    [string]$SdkRoot = (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
    [switch]$SkipEmulator,
    [string]$ProxyHost = '',
    [int]$ProxyPort = 0,
    [string]$CmdlineToolsUrl = 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip'
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$RepoRoot = Split-Path -Parent $PSScriptRoot
$BuildToolsVersion = '35.0.0'
$Abi = 'x86_64'
if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { $Abi = 'arm64-v8a' }
$Avds = [ordered]@{
    'SecureAuth_API35' = "system-images;android-35;google_apis;$Abi"
    'SecureAuth_API26' = "system-images;android-26;google_apis;$Abi"
}

function Step([string]$Message) { Write-Host "==> $Message" -ForegroundColor Cyan }
function Info([string]$Message) { Write-Host "    $Message" }
function Warn([string]$Message) { Write-Host "注意：$Message" -ForegroundColor Yellow }
function Fail([string]$Message) {
    Write-Host "错误：$Message" -ForegroundColor Red
    exit 1
}

function Get-JdkMajor([string]$JdkHome) {
    $release = Join-Path $JdkHome 'release'
    if (-not (Test-Path $release)) { return 0 }
    $line = Select-String -Path $release -Pattern '^JAVA_VERSION="([0-9]+)' | Select-Object -First 1
    if ($null -eq $line) { return 0 }
    return [int]$line.Matches[0].Groups[1].Value
}

function Find-Jdk {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates += (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr')
    $adoptium = Join-Path $env:ProgramFiles 'Eclipse Adoptium'
    if (Test-Path $adoptium) {
        $candidates += (Get-ChildItem $adoptium -Directory -Filter 'jdk-*' |
            Sort-Object Name -Descending | ForEach-Object { $_.FullName })
    }
    $java = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($java) { $candidates += (Split-Path -Parent (Split-Path -Parent $java.Source)) }
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path (Join-Path $candidate 'bin\java.exe')) -and
            ((Get-JdkMajor $candidate) -ge 17)) {
            return $candidate
        }
    }
    return $null
}

function Install-WithWinget([string]$Id, [string]$Name) {
    if (-not $AcceptLicenses) {
        Fail "未找到 $Name。可手动安装后重试，或在用户同意安装及其许可协议后加 -AcceptLicenses 重新运行（将执行 winget install --id $Id）。"
    }
    if (-not (Get-Command winget.exe -ErrorAction SilentlyContinue)) {
        Fail "未找到 $Name，且系统没有 winget。请手动安装 $Name 后重试。"
    }
    Step "用 winget 安装 $Name"
    & winget.exe install --id $Id -e --silent --accept-source-agreements --accept-package-agreements
    if ($LASTEXITCODE -ne 0) { Fail "winget 安装 $Name 失败（退出码 $LASTEXITCODE）" }
}

function Add-UserPath([string[]]$Dirs) {
    $current = [Environment]::GetEnvironmentVariable('Path', 'User')
    if ($null -eq $current) { $current = '' }
    $parts = @($current -split ';' | Where-Object { $_ -ne '' })
    $changed = $false
    foreach ($dir in $Dirs) {
        if ($parts -notcontains $dir) { $parts += $dir; $changed = $true }
        if (($env:Path -split ';') -notcontains $dir) { $env:Path = "$env:Path;$dir" }
    }
    if ($changed) { [Environment]::SetEnvironmentVariable('Path', ($parts -join ';'), 'User') }
}

function Invoke-Sdk([string]$Tool, [string[]]$Arguments, [string]$InputText = $null) {
    $exe = Join-Path $SdkRoot "cmdline-tools\latest\bin\$Tool.bat"
    $all = @("--sdk_root=$SdkRoot") + $Arguments
    if ($ProxyHost -and $Tool -eq 'sdkmanager') {
        $all += @('--proxy=http', "--proxy_host=$ProxyHost", "--proxy_port=$ProxyPort")
    }
    if ($Tool -eq 'avdmanager') { $all = $Arguments }
    # [string] parameters turn $null into '', so test for non-empty input.
    if ($InputText) { $InputText | & $exe @all } else { & $exe @all }
    if ($LASTEXITCODE -ne 0) { Fail "$Tool $($Arguments -join ' ') 失败（退出码 $LASTEXITCODE）" }
}

function Test-Ascii([string]$Text) { return $Text -notmatch '[^\x20-\x7E]' }

if ($env:OS -ne 'Windows_NT') { Fail '此脚本只用于 Windows。macOS / Linux 请参考 docs/guide-dev-environment.md。' }
Step "SDK 目录：$SdkRoot（系统架构 $env:PROCESSOR_ARCHITECTURE，镜像 ABI $Abi）"
# 路径含中文或空格时，Gradle 与模拟器可能出错（常见于中文用户名）。
if (-not (Test-Ascii $SdkRoot) -or $SdkRoot.Contains(' ')) {
    Fail "SDK 路径含非英文字符或空格：$SdkRoot。请指定纯英文路径重新运行，例如：-SdkRoot D:\Android\Sdk"
}

# 1. Git（发布脚本需要 Git Bash）
Step '检查 Git'
if (Get-Command git.exe -ErrorAction SilentlyContinue) {
    Info (& git.exe --version)
} else {
    Install-WithWinget 'Git.Git' 'Git'
    Warn 'Git 已安装，需要新开终端后才能在 PATH 中找到。'
}

# 2. JDK 17+
Step '检查 JDK 17+'
$jdk = Find-Jdk
if (-not $jdk) {
    Install-WithWinget 'EclipseAdoptium.Temurin.17.JDK' 'JDK 17（Eclipse Temurin）'
    $jdk = Find-Jdk
    if (-not $jdk) { Fail 'JDK 安装后仍未找到，请新开终端后重试。' }
}
Info "使用 JDK：$jdk（版本 $(Get-JdkMajor $jdk)）"
$env:JAVA_HOME = $jdk
[Environment]::SetEnvironmentVariable('JAVA_HOME', $jdk, 'User')

# 3. Android 命令行工具
Step '检查 Android 命令行工具'
$sdkmanager = Join-Path $SdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
if (Test-Path $sdkmanager) {
    Info '已安装'
} else {
    New-Item -ItemType Directory -Force -Path $SdkRoot | Out-Null
    $zip = Join-Path $env:TEMP 'secureauth-cmdline-tools.zip'
    $unpack = Join-Path $env:TEMP 'secureauth-cmdline-tools'
    Info "下载 $CmdlineToolsUrl"
    $webArgs = @{ Uri = $CmdlineToolsUrl; OutFile = $zip; UseBasicParsing = $true }
    if ($ProxyHost) { $webArgs.Proxy = "http://${ProxyHost}:$ProxyPort" }
    try {
        Invoke-WebRequest @webArgs
    } catch {
        Fail "下载失败：$($_.Exception.Message)。若链接已失效，请到 https://developer.android.com/studio#command-line-tools-only 复制最新的 Windows 版链接，用 -CmdlineToolsUrl 传入；网络不通时用 -ProxyHost / -ProxyPort。"
    }
    if (Test-Path $unpack) { Remove-Item -Recurse -Force $unpack }
    Expand-Archive -Path $zip -DestinationPath $unpack
    $target = Join-Path $SdkRoot 'cmdline-tools\latest'
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    Move-Item -Path (Join-Path $unpack 'cmdline-tools') -Destination $target
    Remove-Item -Force $zip
    Remove-Item -Recurse -Force $unpack
    Info '已安装到 cmdline-tools\latest'
}

# 4. 许可协议
Step '检查 SDK 许可协议'
$licenseFile = Join-Path $SdkRoot 'licenses\android-sdk-license'
if ($AcceptLicenses) {
    Info '按用户同意自动接受 SDK 许可（-AcceptLicenses）'
    Invoke-Sdk 'sdkmanager' @('--licenses') ((@('y') * 50) -join "`n")
} elseif (-not (Test-Path $licenseFile)) {
    Fail "SDK 许可尚未接受。请用户在终端中运行 `"$sdkmanager`" --licenses 阅读并逐条输入 y；或在用户同意后加 -AcceptLicenses 重新运行本脚本。"
} else {
    Info '已接受'
}

# 5. SDK 组件（用 package 文件传参，避免分号被 cmd 误解析）
$packages = @('platform-tools', 'platforms;android-35', "build-tools;$BuildToolsVersion")
if (-not $SkipEmulator) { $packages += @('emulator') + @($Avds.Values) }
Step "安装 SDK 组件（首次约需下载 1–5 GB）：$($packages -join ', ')"
$packageFile = Join-Path $env:TEMP 'secureauth-sdk-packages.txt'
Set-Content -Path $packageFile -Value $packages -Encoding ASCII
Invoke-Sdk 'sdkmanager' @("--package_file=$packageFile")
Remove-Item -Force $packageFile

# 6. 环境变量（用户级，新开终端生效）
Step '设置用户环境变量 ANDROID_HOME、JAVA_HOME、Path'
[Environment]::SetEnvironmentVariable('ANDROID_HOME', $SdkRoot, 'User')
$env:ANDROID_HOME = $SdkRoot
Add-UserPath @(
    (Join-Path $SdkRoot 'platform-tools'),
    (Join-Path $SdkRoot 'emulator'),
    (Join-Path $SdkRoot 'cmdline-tools\latest\bin'),
    (Join-Path $jdk 'bin')
)

# 7. local.properties（只属于本机，已被 git 忽略）
Step '检查 local.properties'
$localProps = Join-Path $RepoRoot 'local.properties'
if ((Test-Path $localProps) -and (Select-String -Path $localProps -Pattern '^sdk\.dir=' -Quiet)) {
    Info '已存在 sdk.dir，保持不变'
} else {
    $escaped = $SdkRoot.Replace('\', '\\').Replace(':', '\:')
    Add-Content -Path $localProps -Value "sdk.dir=$escaped" -Encoding ASCII
    Info "已写入 sdk.dir=$escaped"
}

# 8. 模拟器
if (-not $SkipEmulator) {
    Step '创建模拟器'
    # 模拟器默认把虚拟机放在 %USERPROFILE%\.android\avd，用户名含中文时会启动失败。
    if (-not (Test-Ascii $env:USERPROFILE) -and -not $env:ANDROID_AVD_HOME) {
        $avdHome = Join-Path (Split-Path -Parent $SdkRoot) 'avd'
        New-Item -ItemType Directory -Force -Path $avdHome | Out-Null
        [Environment]::SetEnvironmentVariable('ANDROID_AVD_HOME', $avdHome, 'User')
        $env:ANDROID_AVD_HOME = $avdHome
        Info "用户目录含非英文字符，模拟器数据改放到 $avdHome（ANDROID_AVD_HOME）"
    }
    $existing = & (Join-Path $SdkRoot 'cmdline-tools\latest\bin\avdmanager.bat') list avd -c
    foreach ($name in $Avds.Keys) {
        if ($existing -contains $name) {
            Info "$name 已存在"
        } else {
            Invoke-Sdk 'avdmanager' @('create', 'avd', '-n', $name, '-k', $Avds[$name], '-d', 'pixel_6') 'no'
            Info "已创建 $name"
        }
    }

    Step '检查模拟器硬件加速'
    & (Join-Path $SdkRoot 'emulator\emulator.exe') -accel-check
    if ($LASTEXITCODE -ne 0) {
        Warn '硬件加速不可用，模拟器会无法启动或极慢。需要用户以管理员身份处理（本脚本不会自动执行）：'
        Info '1) 确认 BIOS 中已开启 CPU 虚拟化（任务管理器 → 性能 → CPU → “虚拟化：已启用”）'
        Info '2) 以管理员身份打开 PowerShell，执行：'
        Info '   Enable-WindowsOptionalFeature -Online -FeatureName HypervisorPlatform -All'
        Info '3) 重启电脑'
    }
}

Write-Host ''
Step '完成'
Info '环境变量已写入用户配置：请关闭并重新打开终端（以及 Claude Code）后生效。'
Info '验证：.\gradlew.bat :core:test :app:assembleDebug'
if (-not $SkipEmulator) {
    Info '启动模拟器：emulator -avd SecureAuth_API35    （或 SecureAuth_API26）'
    Info '设备测试：.\gradlew.bat :app:connectedDebugAndroidTest    （会清空 debug 包数据，只在模拟器上跑）'
}
