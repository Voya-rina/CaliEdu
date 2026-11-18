<#
  CaliEdu 一键装到手机

  直接双击本文件就能跑（你的机器上 .ps1 已关联到 PowerShell）。
  开跑后先问你要 release 还是 debug，再往下走。

  也可以带参数跑：
    install.ps1 -Channel debug      跳过选择，直接装 debug
    install.ps1 -Fast               不编译，装现有安装包
    install.ps1 -Check              只体检：adb / JDK / 包 / 设备
    install.ps1 -Uninstall          卸载（会清掉学习记录）
    install.ps1 -NoPause            跑完不等回车（给脚本调）

  放哪都行：脚本自己 cd 到所在目录再找工程。
#>
[CmdletBinding(PositionalBinding = $false)]
param(
    [ValidateSet('release', 'debug')][string]$Channel = 'release',
    [switch]$Fast,
    [switch]$Check,
    [switch]$Uninstall,
    [switch]$NoPause,
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$Rest
)

$ErrorActionPreference = 'Continue'
$Root = $PSScriptRoot
if (-not $Root) { $Root = Split-Path -Parent $MyInvocation.MyCommand.Path }
Set-Location -LiteralPath $Root

$AppId = 'com.caliedu'
$Activity = 'com.caliedu/.MainActivity'
$script:Adb = $null

# ---------------------------------------------------------------- 输出

function Say {
    param([string]$Text = '', [string]$Color = 'Gray')
    Write-Host $Text -ForegroundColor $Color
}

function Step {
    param([string]$Index, [string]$Text)
    Write-Host ("  [{0}] {1}" -f $Index, $Text) -ForegroundColor Gray
}

# 双击运行时窗口不能一闪就没，所以失败也要停一下
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

# ---------------------------------------------------------------- 工具链

function Format-Size {
    param([long]$Bytes)
    if ($Bytes -ge 1MB) { return ('{0:N1} MB' -f ($Bytes / 1MB)) }
    return ('{0:N0} KB' -f ($Bytes / 1KB))
}

function Find-Adb {
    $candidates = @()
    if ($env:ANDROID_SDK_ROOT) { $candidates += (Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe') }
    if ($env:ANDROID_HOME) { $candidates += (Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe') }

    $prop = Join-Path $Root 'local.properties'
    if (Test-Path -LiteralPath $prop) {
        $line = Select-String -LiteralPath $prop -Pattern '^\s*sdk\.dir\s*=' | Select-Object -First 1
        if ($line) {
            # Java properties 写法: C\:\\Users\\... 先把转义还原
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
    # 有 jdk 就先挑 jdk（jdk-21 排在 jdk-17 前），没有再用 Studio 自带的 jbr
    $jdk = $usable | Where-Object { $_.Name -match 'jdk' } | Sort-Object Name -Descending | Select-Object -First 1
    if ($jdk) { return $jdk.FullName }
    if ($usable.Count -gt 0) { return $usable[0].FullName }
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

# ---------------------------------------------------------------- 通道

$Apks = [ordered]@{
    release = Join-Path $Root 'app\build\outputs\apk\release\app-release.apk'
    debug   = Join-Path $Root 'app\build\outputs\apk\debug\app-debug.apk'
}
$Tasks = @{
    release = ':app:assembleRelease'
    debug   = ':app:assembleDebug'
}

function Describe-Apk {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return '未编译' }
    $item = Get-Item -LiteralPath $Path
    return ('{0}   {1:MM-dd HH:mm}' -f (Format-Size $item.Length), $item.LastWriteTime)
}

function Read-ChannelMenu {
    Say '  装哪个包？'
    Say ("    1  release   {0}   R8 压缩，体积小、启动快，日常用" -f (Describe-Apk $Apks['release']))
    Say ("    2  debug     {0}   能断点、日志完整，查问题用" -f (Describe-Apk $Apks['debug']))
    Say ''
    while ($true) {
        $answer = Read-Host '  输入 1 或 2 回车（直接回车 = release）'
        if ([string]::IsNullOrWhiteSpace($answer)) { return 'release' }
        switch ($answer.Trim().ToLowerInvariant()) {
            '1' { return 'release' }
            '2' { return 'debug' }
            'release' { return 'release' }
            'debug' { return 'debug' }
            default { Say '  只能输 1 或 2' 'Yellow' }
        }
    }
}

# 参数里给了 debug/release 就不再问
$channelGiven = $PSBoundParameters.ContainsKey('Channel')
foreach ($token in @($Rest)) {
    if ([string]::IsNullOrWhiteSpace($token)) { continue }
    switch ($token.ToLowerInvariant()) {
        'debug' { $Channel = 'debug'; $channelGiven = $true }
        'release' { $Channel = 'release'; $channelGiven = $true }
        '/fast' { $Fast = $true }
        '/check' { $Check = $true }
        '/uninstall' { $Uninstall = $true }
        '/nopause' { $NoPause = $true }
        default { Say ("  [!] 忽略不认识的参数: {0}" -f $token) 'Yellow' }
    }
}

Say ''
Say '  CaliEdu 一键安装'
Say '  ---------------------------------------------'

if (-not $channelGiven -and -not $Uninstall -and -not $NoPause) {
    $Channel = Read-ChannelMenu
    Say ("  选好了：{0}" -f $Channel)
    Say ''
}

$apk = $Apks[$Channel]
$task = $Tasks[$Channel]

if ($Check) {
    Say ("  包     release  {0}" -f (Describe-Apk $Apks['release']))
    Say ("         debug    {0}" -f (Describe-Apk $Apks['debug']))
}
else {
    Say ("  通道   {0}" -f $Channel)
    Say ("  包     {0}" -f $apk)
}
Say ''

# ---------------------------------------------------------------- 主流程

$script:Adb = Find-Adb
if (-not $script:Adb) {
    Say '  [X] 找不到 adb.exe' 'Red'
    Say '      装 SDK 的 platform-tools，或把它的目录加进 PATH。'
    Say '      常见位置: %LOCALAPPDATA%\Android\Sdk\platform-tools'
    Finish 1
}
Step '1/5' ("adb  {0}" -f $script:Adb)

$jdk = Find-Jdk
if ($jdk) {
    $env:JAVA_HOME = $jdk
    Step '2/5' ("JDK  {0}" -f $jdk)
}
else {
    Step '2/5' 'JDK  没找到，已有安装包时不影响安装'
}

& $script:Adb start-server 2>$null | Out-Null
$devices = Get-DeviceList

if ($Check) {
    Say ''
    Say '  --- 体检 ---'
    if ($devices.Count -eq 0) {
        Say '  设备  无'
    }
    else {
        foreach ($d in $devices) { Say ("  设备  {0}  {1}" -f $d.Serial, $d.State) }
    }
    if ($jdk) { Say '  JDK   可用' } else { Say '  JDK   缺失，只能装现成包' }
    Finish 0
}

function Select-Device {
    param([object[]]$Devices)

    $ready = @($Devices | Where-Object { $_.State -eq 'device' })
    foreach ($d in @($Devices | Where-Object { $_.State -ne 'device' })) {
        if ($d.State -eq 'unauthorized') {
            Say ("  [!] {0} 还没授权：看手机弹窗点『允许』，勾上『一直允许』" -f $d.Serial) 'Yellow'
        }
        elseif ($d.State -eq 'offline') {
            Say ("  [!] {0} 离线：拔插一次数据线，或确认没被别的程序独占" -f $d.Serial) 'Yellow'
        }
    }

    if ($ready.Count -eq 0) { return $null }
    if ($ready.Count -eq 1) { return $ready[0].Serial }

    Say ("  连着 {0} 台可用设备：" -f $ready.Count)
    for ($i = 0; $i -lt $ready.Count; $i++) {
        Say ("    {0}. {1}" -f ($i + 1), $ready[$i].Serial)
    }
    if ($NoPause) { return $ready[0].Serial }

    $pick = Read-Host '  用哪台，输入序号回车（直接回车=1）'
    if ([string]::IsNullOrWhiteSpace($pick)) { $pick = '1' }
    $index = 0
    if (-not [int]::TryParse($pick, [ref]$index) -or $index -lt 1 -or $index -gt $ready.Count) {
        Say '  [X] 序号不对' 'Red'
        return $null
    }
    return $ready[$index - 1].Serial
}

$serial = $null
while (-not $serial) {
    $serial = Select-Device -Devices $devices
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
    $devices = Get-DeviceList
}
Step '3/5' ("设备 {0}" -f $serial)

if ($Uninstall) {
    Say ''
    if (-not $NoPause) {
        $answer = Read-Host '  确认卸载手机上所有 CaliEdu 数据？[y/N]'
        if ($answer -notmatch '^[yY]') {
            Say '  已取消。'
            Finish 0
        }
    }
    & $script:Adb -s $serial uninstall $AppId
    Say '  已卸载。'
    Finish 0
}

if ($Fast) {
    Step '4/5' '跳过编译'
    if (-not (Test-Path -LiteralPath $apk)) {
        Say ("  [X] -Fast 要求安装包已经存在，但没找到: {0}" -f $apk) 'Red'
        Say '      去掉 -Fast 先编译一次。'
        Finish 1
    }
}
else {
    if (-not $env:JAVA_HOME) {
        Say '  [X] 要编译但没找到 JDK 17/21。' 'Red'
        Say '      装一个 JDK，或把 JAVA_HOME 指过去。'
        Say '      只想装现有包的话: install.ps1 -Fast'
        Finish 1
    }
    Step '4/5' ("编译 {0} 包，首次或刚改过代码会久一点" -f $Channel)
    Say ''
    & (Join-Path $Root 'gradlew.bat') $task '--console=plain'
    if ($LASTEXITCODE -ne 0) {
        Say ''
        Say '  [X] 编译失败，往上翻 Gradle 的报错。' 'Red'
        Finish 1
    }
    if (-not (Test-Path -LiteralPath $apk)) {
        Say ("  [X] 编译说成功，但没找到 {0}" -f $apk) 'Red'
        Finish 1
    }
}

$apkInfo = Get-Item -LiteralPath $apk
Say ''
Step '5/5' ("安装 {0}" -f (Format-Size $apkInfo.Length))

function Invoke-Install {
    param([string[]]$Extra)
    $arguments = @('-s', $serial, 'install') + $Extra + @($apk)
    $output = & $script:Adb @arguments 2>&1
    return [pscustomobject]@{
        Ok  = [bool]($output -match 'Success')
        Out = ($output | Out-String).Trim()
    }
}

$result = Invoke-Install -Extra @('-r')
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
        $result = Invoke-Install -Extra @('-r')
        if ($result.Out) { Say $result.Out }
        if (-not $result.Ok) { Fail '卸载后还是装不上，看上面的 adb 输出。' 4 }
    }
    elseif ($result.Out -match 'VERSION_DOWNGRADE') {
        Say '  [!] 手机上的版本号更高，按降级安装再试一次' 'Yellow'
        $result = Invoke-Install -Extra @('-r', '-d')
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
Say ("  装好了  CaliEdu {0}" -f $version) 'Green'
Say ("  通道 {0}   包 {1}" -f $Channel, (Format-Size $apkInfo.Length))
Say '  手机上已经打开了，解锁看一眼就行。'
Finish 0
