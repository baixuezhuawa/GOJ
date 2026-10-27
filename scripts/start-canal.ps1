param(
    [string]$CanalHome = 'D:\Work\Canal',
    [switch]$Restart
)
$ErrorActionPreference = 'Stop'

# 使用安装包既有配置和复制账号，不在启动参数或日志中输出密码。
$canalRoot = (Resolve-Path -LiteralPath $CanalHome).Path
$config = Join-Path $canalRoot 'conf\canal.properties'
if (-not (Test-Path -LiteralPath $config)) { throw 'Canal 配置文件不存在' }
$logDirectory = Join-Path $canalRoot 'logs'
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
$pidFile = Join-Path $logDirectory 'goj-canal.pid'

# 只管理此脚本记录且命令行确实属于此安装目录的 Canal 进程。
if (Test-Path -LiteralPath $pidFile) {
    $processId = [int](Get-Content -LiteralPath $pidFile)
    $existing = Get-CimInstance Win32_Process -Filter "ProcessId=$processId"
    if ($existing -and $existing.CommandLine.Contains('com.alibaba.otter.canal.deployer.CanalLauncher') -and
            $existing.CommandLine.Contains($canalRoot)) {
        if (-not $Restart) { Write-Host "Canal 已运行，进程 ID：$processId"; return }
        Stop-Process -Id $processId
        Wait-Process -Id $processId -ErrorAction SilentlyContinue
    }
}

$java = (Get-Command java.exe).Source
$arguments = @('-Xms128m', '-Xmx512m', '-Dfile.encoding=UTF-8',
    "`"-Dcanal.conf=$config`"",
    "`"-Dlogback.configurationFile=$canalRoot\conf\logback.xml`"",
    '-cp', "`"$canalRoot\conf;$canalRoot\lib\*`"",
    'com.alibaba.otter.canal.deployer.CanalLauncher')
$process = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory (Join-Path $canalRoot 'bin') `
    -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDirectory 'goj-console.log') `
    -RedirectStandardError (Join-Path $logDirectory 'goj-console-error.log')
$process.Id | Set-Content -LiteralPath $pidFile
Start-Sleep -Seconds 2
if ($process.HasExited) { throw "Canal 启动失败，请查看 $logDirectory\goj-console-error.log" }
Write-Host "Canal 已启动，进程 ID：$($process.Id)。日志目录：$logDirectory"
Write-Host '请检查 logs\goj\goj.log 确认 MySQL 复制连接正常；进程存活不代表同步成功。'
