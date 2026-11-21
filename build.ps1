<#
  CaliEdu 一键构建 + 安装（合并版）

  双击本文件就能跑。打开先扫一遍环境（adb / JDK / 包 / 设备），
  然后输一个数字（或几个词）就开干，不再多级选择。

  一次输入的写法（数字或单词都行）：
    1 / build              构建 release
    2 / build debug        构建 debug
    3 / install            构建 release 并装到手机（直接回车 = 这个）
    4 / install debug      构建 debug 并装到手机
    5 / fast               装现成的 release 包（不编译）
    6 / fast debug         装现成的 debug 包
    7 / uninstall          卸载手机上的 CaliEdu（清学习记录）
    8 / refresh            清屏并重新扫一遍环境状态

  带参数跑：
    build.ps1 install debug -Clean
    build.ps1 fast
    build.ps1 build debug -Clean -NoPause
    build.ps1 -Channel debug -NoPause      # 等价于 install debug

  构建产物固定在 app\build\outputs\apk\<通道>\ 下。
#>
[CmdletBinding(PositionalBinding = $false)]
param(
    [ValidateSet('release', 'debug')][string]$Channel = '',
    [switch]$Clean,
    [switch]$Fast,
    [switch]$NoPause,
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$Rest
)

$ErrorActionPreference = 'Continue'
$Root = $PSScriptRoot
if (-not $Root) { $Root = Split-Path -Parent $MyInvocation.MyCommand.Path }
Set-Location -LiteralPath $Root

$AppId    = 'com.caliedu'
$Activity = 'com.caliedu/.MainActivity'
$script:Adb = $null
$script:Jdk = $null

$Apks = [ordered]@{
    release = Join-Path $Root 'app\build\outputs\apk\release\app-release.apk'
    debug   = Join-Path $Root 'app\build\outputs\apk\debug\app-debug.apk'
}
$Tasks = @{
    release = ':app:assembleRelease'
    debug   = ':app:assembleDebug'
}

# ---------------------------------------------------------------- 输出

function Say {
    param([string]$Text = '', [string]$Color = 'Gray')
    Write-Host $Text -ForegroundColor $Color
}

function Step {
    param([string]$Text, [string]$Index)
    if ($Index) { Write-Host ("  [{0}] {1}" -f $Index, $Text) -ForegroundColor Gray }
    else        { Write-Host ("  > {0}" -f $Text) -ForegroundColor Gray }
}

function Finish {
    param([int]$Code = 0)
    Say ''
    if (-not $NoPause) { Read-Host '按回车关闭' | Out-Null }
    exit $Code
}

function Fail {
    param([string]$Message, [int]$Code = 1)
    Say ''
    if ($Message) { Say ("  [X] {0}" -f $Message) 'Red' }
    Finish -Code $Code
}

# ---------------------------------------------------------------- 工具

function Format-Size {
    param([long]$Bytes)
    if ($Bytes -ge 1MB) { return ('{0:N1} MB' -f ($Bytes / 1MB)) }
    return ('{0:N0} KB' -f ($Bytes / 1KB))
}

function Find-Jdk {
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        return $env:JAVA_HOME
    }
    $dirs = @()
    foreach ($base in @('C:\Program Files\Java', 'D:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium')) {
        if (Test-Path -LiteralPath $base) {
            $dirs += Get-ChildItem -LiteralPath $base -Directory -ErrorAction SilentlyContinue
        }
    }
    $dirs += Get-Item -LiteralPath 'D:\Program Files\Android\Android Studio\jbr' -ErrorAction SilentlyContinue
    $dirs += Get-Item -LiteralPath 'C:\Program Files\Android\Android Studio\jbr' -ErrorAction SilentlyContinue

    $usable = @($dirs | Where-Object { $_ } | Where-Object {
                Test-Path -LiteralPath (Join-Path $_.FullName 'bin\java.exe')
            })
    $jdk = $usable | Where-Object { $_.Name -match 'jdk' } | Sort-Object Name -Descending | Select-Object -First 1
    if ($jdk) { return $jdk.FullName }
    if ($usable.Count -gt 0) { return $usable[0].FullName }
    return $null
}

function Find-Adb {
    $candidates = @()
    if ($env:ANDROID_SDK_ROOT) { $candidates += (Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe') }
    if ($env:ANDROID_HOME)     { $candidates += (Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe') }

    $prop = Join-Path $Root 'local.properties'
    if (Test-Path -LiteralPath $prop) {
        $line = Select-String -LiteralPath $prop -Pattern '^\s*sdk\.dir\s*=' | Select-Object -First 1
        if ($line) {
            $sdk = ($line.Line -split '=', 2)[1].Trim()
            $sdk = $sdk -replace '\\\\', '\' -replace '\\:', ':' -replace '/', '\'
            $candidates += (Join-Path $sdk 'platform-tools\adb.exe')
        }
    }

    if ($env:LOCALAPPDATA) { $candidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe') }
    $candidates += 'D:\ProgramsA\Android\Android platform tools\adb.exe'
    $candidates += 'C:\Program Files\Android\Sdk\platform-tools\adb.exe'

    foreach ($path in $candidates) {
        if ($path -and (Test-Path -LiteralPath $path)) { return $path }
    }
    $cmd = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

function Get-DeviceList {
    $lines = & $script:Adb devices 2>$null
    $list = @()
    foreach ($line in $lines) {
        if ($line -match '^\s*(\S+)\s+(device|unauthorized|offline|bootloader|recovery)\s*$') {
            $list += [pscustomobject]@{ Serial = $Matches[1]; State = $Matches[2] }
        }
    }
    return $list
}

function Get-VersionName {
    $file = Join-Path $Root 'app\build.gradle.kts'
    if (-not (Test-Path -LiteralPath $file)) { return '?' }
    $line = Select-String -LiteralPath $file -Pattern 'versionName\s*=\s*"([^"]+)"' | Select-Object -First 1
    if ($line -and $line.Matches.Count -gt 0) { return $line.Matches[0].Groups[1].Value }
    return '?'
}

function Get-VersionCode {
    $file = Join-Path $Root 'app\build.gradle.kts'
    if (-not (Test-Path -LiteralPath $file)) { return '?' }
    $line = Select-String -LiteralPath $file -Pattern 'versionCode\s*=\s*(\d+)' | Select-Object -First 1
    if ($line -and $line.Matches.Count -gt 0) { return $line.Matches[0].Groups[1].Value }
    return '?'
}

function Describe-Apk {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return '未编译' }
    $item = Get-Item -LiteralPath $Path
    return ('{0}  {1:MM-dd HH:mm}' -f (Format-Size $item.Length), $item.LastWriteTime)
}

# ---------------------------------------------------------------- 开屏状态

function Show-Header {
    Say ''
    Say '  CaliEdu 构建 + 安装'
    Say '  ---------------------------------------------'
    Say ("  版本   {0}  ({1})" -f (Get-VersionName), (Get-VersionCode))
    Say ''
}

function Show-Status {
    # [修复] 恢复环境扫描；refresh 时能真正重扫
    Say '  环境'
    $script:Adb = Find-Adb
    if ($script:Adb) { Say ("    adb    {0}" -f $script:Adb) }
    else             { Say '    adb    不可用' 'Yellow' }

    $script:Jdk = Find-Jdk
    if ($script:Jdk) { Say ("    JDK    {0}" -f $script:Jdk) }
    else             { Say '    JDK    不可用' 'Yellow' }

    Say '  当前已存在的包'
    Say ("    release  {0}" -f (Describe-Apk $Apks['release']))
    Say ("    debug    {0}" -f (Describe-Apk $Apks['debug']))

    Say '  设备'
    if (-not $script:Adb) {
        Say '    无（adb 不可用）'
    }
    else {
        & $script:Adb start-server 2>$null | Out-Null
        $devices = Get-DeviceList
        if ($devices.Count -eq 0) {
            Say '    无（插好数据线、开 USB 调试后再选安装类选项）'
        }
        else {
            foreach ($d in $devices) {
                $tag = switch ($d.State) {
                    'device'       { '可用' }
                    'unauthorized' { '未授权，请在手机上点允许' }
                    'offline'      { '离线' }
                    default        { $d.State }
                }
                if ($d.State -eq 'device') { Say ("    {0}  {1}" -f $d.Serial, $tag) 'Green' }
                else                       { Say ("    {0}  {1}" -f $d.Serial, $tag) 'Yellow' }
            }
        }
    }
    Say ''
}

# ---------------------------------------------------------------- 一次输入

function Read-Command {
    Say '  请选择：'
    Say '    1  构建 release                5  装现成 release 包（不编译）'
    Say '    2  构建 debug                  6  装现成 debug 包（不编译）'
    Say '    3  构建 release 并装到手机     7  卸载手机上的 CaliEdu'
    Say '    4  构建 debug 并装到手机       8  刷新'
    Say ''
    while ($true) {
        $answer = Read-Host '  直接回车 = 3（构建 release 并装到手机）'
        if ([string]::IsNullOrWhiteSpace($answer)) { return @{ Action = 'all'; Channel = 'release'; Fast = $false } }

        $result = Parse-Command -Text $answer.Trim()
        if ($result) { return $result }
        Say '  请输入 1-8 的数字，或 build / install / fast / uninstall / refresh' 'Yellow'
    }
}

function Parse-Command {
    # [修复] 参数不能叫 $Input——那是 PS 的自动变量，会导致传入值读不到
    param([string]$Text)

    if ([string]::IsNullOrWhiteSpace($Text)) { return $null }

    # 纯数字直接映射
    if ($Text -match '^\d$') {
        switch ($Text) {
            '1' { return @{ Action = 'build';     Channel = 'release'; Fast = $false } }
            '2' { return @{ Action = 'build';     Channel = 'debug';   Fast = $false } }
            '3' { return @{ Action = 'all';       Channel = 'release'; Fast = $false } }
            '4' { return @{ Action = 'all';       Channel = 'debug';   Fast = $false } }
            '5' { return @{ Action = 'install';   Channel = 'release'; Fast = $true  } }
            '6' { return @{ Action = 'install';   Channel = 'debug';   Fast = $true  } }
            '7' { return @{ Action = 'uninstall'; Channel = '';        Fast = $false } }
            '8' { return @{ Action = 'refresh';   Channel = '';        Fast = $false } }
        }
    }

    # 单词 / 词组：build / build debug / install debug / fast / uninstall ...
    $action = ''; $channel = ''; $fast = $false
    foreach ($word in ($Text -split '\s+')) {
        if ([string]::IsNullOrWhiteSpace($word)) { continue }   # [修复] 跳过空词
        switch ($word.ToLowerInvariant()) {
            'build'     { $action = 'build' }
            'install'   { if ($action -eq 'build') { $action = 'all' } else { $action = 'install' } }
            'fast'      { $action = 'install'; $fast = $true }
            'uninstall' { $action = 'uninstall' }
            'refresh'   { $action = 'refresh' }
            'release'   { $channel = 'release' }
            'debug'     { $channel = 'debug' }
            default     { return $null }   # 有不认识的词就当没看懂
        }
    }
    if (-not $action) { return $null }

    $result = @{ Action = $action; Channel = $channel; Fast = $fast }
    # 兜底通道
    if ($action -ne 'uninstall' -and $action -ne 'refresh' -and -not $result.Channel) { $result.Channel = 'release' }
    return $result
}

# ---------------------------------------------------------------- 构建 / 安装 / 卸载

function Invoke-Build {
    param([string]$Channel)

    if (-not $script:Jdk) { $script:Jdk = Find-Jdk }
    if (-not $script:Jdk) {
        Say '  [X] 无法找到 JDK。Gradle 需要一个 JDK（17 或 21）。' 'Red'
        Fail $null 1
    }
    $env:JAVA_HOME = $script:Jdk
    Step ("JDK  {0}" -f $script:Jdk)

    $gradlew = Join-Path $Root 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $gradlew)) { Fail '当前目录没有 gradlew.bat，这个脚本要放在工程根目录。' 1 }

    $tasks = switch ($Channel) {
        'debug' { @(':app:assembleDebug') }
        default { @(':app:assembleRelease') }
    }

    if ($Clean) {
        Step 'clean'
        & $gradlew 'clean' '--console=plain'
        if ($LASTEXITCODE -ne 0) { Fail 'clean 失败' 1 }
    }

    $started = Get-Date
    foreach ($task in $tasks) {
        $name = $task -replace ':app:assemble', ''
        Step ("编译 {0}" -f $name)
        Say ''
        & $gradlew $task '--console=plain'
        if ($LASTEXITCODE -ne 0) {
            Say ''
            Fail ("{0} 编译失败" -f $name) 1
        }
    }
    $elapsed = [int]((Get-Date) - $started).TotalSeconds

    Say ''
    Say '  ---------------------------------------------'
    $built = @()
    foreach ($key in @($Apks.Keys)) {
        if ($key -ne $Channel) { continue }
        $path = $Apks[$key]
        if (-not (Test-Path -LiteralPath $path)) { continue }
        $item = Get-Item -LiteralPath $path
        $built += $path
        Say ("  [ok] {0,-8} {1}" -f $key, (Format-Size $item.Length)) 'Green'
        Say ("       {0}" -f $path)
    }

    if ($built.Count -eq 0) { Fail '成功编译，但未找到安装包' 1 }

    Say ''
    Say ("  CaliEdu {0} 构建完成，用时 {1} 秒" -f (Get-VersionName), $elapsed) 'Green'
}

function Select-Device {
    param([object[]]$Devices)

    $ready = @($Devices | Where-Object { $_.State -eq 'device' })
    foreach ($d in @($Devices | Where-Object { $_.State -ne 'device' })) {
        if ($d.State -eq 'unauthorized') {
            Say ("  [!] {0} 还未授权，手机弹窗点「允许」" -f $d.Serial) 'Yellow'
        }
        elseif ($d.State -eq 'offline') {
            Say ("  [!] {0} 当前为离线状态：拔插一次数据线，或确认没被别的程序独占" -f $d.Serial) 'Yellow'
        }
    }

    if ($ready.Count -eq 0) { return $null }
    if ($ready.Count -eq 1) { return $ready[0].Serial }

    Say ("  连着 {0} 台可用设备：" -f $ready.Count)
    for ($i = 0; $i -lt $ready.Count; $i++) {
        Say ("    {0}. {1}" -f ($i + 1), $ready[$i].Serial)
    }
    if ($NoPause) { return $ready[0].Serial }

    $pick = Read-Host '  请选择设备（直接回车=1）'
    if ([string]::IsNullOrWhiteSpace($pick)) { $pick = '1' }
    $index = 0
    if (-not [int]::TryParse($pick, [ref]$index) -or $index -lt 1 -or $index -gt $ready.Count) {
        Say '  [X] 序号不对' 'Red'
        return $null
    }
    return $ready[$index - 1].Serial
}

function Wait-ForDevice {
    $serial = $null
    while (-not $serial) {
        $serial = Select-Device -Devices (Get-DeviceList)
        if ($serial) { break }

        Say '  [X] 没有可用的手机' 'Red'
        Say '      1. 数据线插好，USB 用途选"传输文件 / MTP"'
        Say '      2. 开发者选项里打开"USB 调试"'
        Say '      3. 手机弹窗点"允许"'
        Say '      无线调试也可以: adb connect 手机IP:端口'
        if ($NoPause) { Finish 2 }

        Say ''
        Read-Host '  接好后按回车重试' | Out-Null
        & $script:Adb start-server 2>$null | Out-Null
    }
    return $serial
}

function Invoke-Install {
    param([string]$Channel, [bool]$BuildFirst)

    if (-not $script:Adb) { $script:Adb = Find-Adb }
    if (-not $script:Adb) {
        Say '  [X] 找不到 adb.exe' 'Red'
        Say '      装 SDK 的 platform-tools，或把它的目录加进 PATH。'
        Finish 1
    }
    Step '1/5' ("adb  {0}" -f $script:Adb)

    if (-not $script:Jdk) { $script:Jdk = Find-Jdk }
    if ($script:Jdk) {
        $env:JAVA_HOME = $script:Jdk
        Step '2/5' ("JDK  {0}" -f $script:Jdk)
    }
    else {
        Step '2/5' 'JDK  没找到'
        if ($BuildFirst) {
            Say '  [X] 要编译但没找到 JDK 17/21。装现成包的话选 fast / 5 / 6。' 'Red'
            Finish 1
        }
    }

    & $script:Adb start-server 2>$null | Out-Null
    $serial = Wait-ForDevice
    Step '3/5' ("设备 {0}" -f $serial)

    $apk = $Apks[$Channel]
    if ($BuildFirst) {
        Step '4/5' ("编译 {0} 包" -f $Channel)
        Say ''
        # [修复] 构建段支持 -Clean
        if ($Clean) {
            Step 'clean'
            & (Join-Path $Root 'gradlew.bat') 'clean' '--console=plain'
            if ($LASTEXITCODE -ne 0) {
                Say ''
                Say '  [X] clean 失败' 'Red'
                Finish 1
            }
        }
        & (Join-Path $Root 'gradlew.bat') $Tasks[$Channel] '--console=plain'
        if ($LASTEXITCODE -ne 0) {
            Say ''
            Say '  [X] 编译失败' 'Red'
            Finish 1
        }
    }
    else {
        Step '4/5' '跳过编译'
    }
    if (-not (Test-Path -LiteralPath $apk)) {
        Say ("  [X] 没找到安装包: {0}" -f $apk) 'Red'
        if (-not $BuildFirst) { Say '      先构建一次（选 1 / 2 / 3 / 4）。' }
        Finish 1
    }

    $apkInfo = Get-Item -LiteralPath $apk
    Say ''
    Step '5/5' ("安装 {0}" -f (Format-Size $apkInfo.Length))

    function Invoke-AdbInstall {
        param([string[]]$Extra)
        $arguments = @('-s', $serial, 'install') + $Extra + @($apk)
        $output = & $script:Adb @arguments 2>&1
        return [pscustomobject]@{
            Ok  = [bool]($output -match 'Success')
            Out = ($output | Out-String).Trim()
        }
    }

    $result = Invoke-AdbInstall -Extra @('-r')
    if ($result.Out) { Say $result.Out }

    if (-not $result.Ok) {
        if ($result.Out -match 'UPDATE_INCOMPATIBLE|signatures do not match|INSTALL_FAILED_UPDATE') {
            Say ''
            Say '  [!] 手机上已有的 CaliEdu 不是同一个签名，装不上去。' 'Yellow'
            Say '      只能先卸载。卸载会一起删掉本地记录和错题本。'
            if ($NoPause) { Finish 3 }
            $answer = Read-Host '  卸载旧版后继续？[y/N]'
            if ($answer -notmatch '^[yY]') { Finish 3 }
            & $script:Adb -s $serial uninstall $AppId | Out-Null
            $result = Invoke-AdbInstall -Extra @('-r')
            if ($result.Out) { Say $result.Out }
            if (-not $result.Ok) { Fail '卸载后还是装不上，看上面的 adb 输出。' 4 }
        }
        elseif ($result.Out -match 'VERSION_DOWNGRADE') {
            Say '  [!] 手机上的版本号更高，按降级安装再试一次' 'Yellow'
            $result = Invoke-AdbInstall -Extra @('-r', '-d')
            if ($result.Out) { Say $result.Out }
            if (-not $result.Ok) { Fail '降级安装也失败了。' 4 }
        }
        else {
            Say '  [X] 安装失败，看上面的 adb 输出。' 'Red'
            Say '      INSTALL_FAILED_INSUFFICIENT_STORAGE 就是手机空间不够。'
            Finish 4
        }
    }

    Say ''
    Say '  [ok] 启动'
    & $script:Adb -s $serial shell am force-stop $AppId 2>$null | Out-Null
    & $script:Adb -s $serial shell am start -n $Activity 2>$null | Out-Null
    Start-Sleep -Seconds 2

    $version = ''
    foreach ($line in (& $script:Adb -s $serial shell dumpsys package $AppId 2>$null)) {
        if ($line -match 'versionName=(\S+)') { $version = $Matches[1]; break }
    }

    Say ''
    Say '  ---------------------------------------------'
    Say ("  CaliEdu {0} 安装完成" -f $version) 'Green'
    Say ("  通道 {0}   包 {1}" -f $Channel, (Format-Size $apkInfo.Length))
}

function Invoke-Uninstall {
    if (-not $script:Adb) { $script:Adb = Find-Adb }
    if (-not $script:Adb) {
        Say '  [X] 找不到 adb.exe' 'Red'
        Finish 1
    }
    Step ("adb  {0}" -f $script:Adb)

    & $script:Adb start-server 2>$null | Out-Null
    $serial = Wait-ForDevice

    Say ''
    if (-not $NoPause) {
        $answer = Read-Host '  确认卸载手机上所有 CaliEdu 数据？[y/N]'
        if ($answer -notmatch '^[yY]') {
            Say '  已取消。'
            return
        }
    }
    & $script:Adb -s $serial uninstall $AppId
    Say '  已卸载。'
}

# ---------------------------------------------------------------- 参数解析

# 兼容 -Channel 参数
$cliChannel = $PSBoundParameters.ContainsKey('Channel') -and $Channel

# 位置参数合并成一句话，交给同一个解析器
$cliInput = (@($Rest) | Where-Object { $_ -and $_.Trim() }) -join ' '
$command = $null

if ($cliInput) {
    # /clean /fast /nopause 这类开关先摘出来
    $words = @()
    foreach ($token in @($Rest)) {
        if ([string]::IsNullOrWhiteSpace($token)) { continue }
        switch ($token.ToLowerInvariant()) {
            '/clean'   { $Clean = $true }
            '/fast'    { $Fast = $true }
            '/nopause' { $NoPause = $true }
            default    { $words += $token }
        }
    }
    $cliInput = ($words -join ' ').Trim()
    if ($cliInput) {
        $command = Parse-Command -Text $cliInput
        if (-not $command) {
            Say ("  [!] 参数不合法: {0}，按默认流程" -f $cliInput) 'Yellow'
        }
    }
}

# ---------------------------------------------------------------- 主流程

Show-Header
Show-Status

# 没有命令行参数才问；问了就一次输入定死，不再追问
while (-not $command) {
    # [修复] 把 $Fast 也算上，单独 -Fast / /fast 才能直接进 fast 安装
    if ($cliChannel -or $NoPause -or $Fast) {
        $command = @{
            Action  = if ($Fast) { 'install' } else { 'all' }
            Channel = if ($Channel) { $Channel } else { 'release' }
            Fast    = $Fast
        }
    }
    else {
        $command = Read-Command
        if ($command.Action -eq 'refresh') {
            Clear-Host
            Show-Header
            Show-Status
            $command = $null    # 刷完继续问
        }
    }
}

# [修复] -Fast 与 install 组合统一处理；build 场景忽略 fast
if ($Fast) {
    if ($command.Action -eq 'all' -or $command.Action -eq 'install') {
        $command.Action = 'install'
        $command.Fast = $true
    }
}

if ($Clean) { Say '  附加   先 clean' }
if ($command.Fast) { Say '  附加   跳过编译' }
if ($command.Channel) { Say ("  通道   {0}" -f $command.Channel) }
Say ''

switch ($command.Action) {
    'build' {
        Invoke-Build -Channel $command.Channel
    }
    'all' {
        Invoke-Build -Channel $command.Channel
        Say ''
        Invoke-Install -Channel $command.Channel -BuildFirst $false
    }
    'install' {
        Invoke-Install -Channel $command.Channel -BuildFirst (-not $command.Fast)
    }
    'uninstall' {
        Invoke-Uninstall
    }
}

Finish 0