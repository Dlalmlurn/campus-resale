# ============================================================
# fix-java-home.ps1
# 自动检测系统已安装的 JDK，注入 JAVA_HOME 到当前终端会话。
# 用法：在 PowerShell 中执行  . .\scripts\fix-java-home.ps1
#       （注意开头的点和空格，表示"dot-source"，让变量在当前会话生效）
# ============================================================

Write-Host ""
Write-Host "=== JAVA_HOME 智能修复脚本 ===" -ForegroundColor Cyan
Write-Host ""

# ─────────────────────────────────────────
# 步骤 1：尝试通过 where.exe java 反推 JDK 根目录
# ─────────────────────────────────────────
function Find-JavaHomeFromPath {
    try {
        $javaExe = (where.exe java 2>$null | Select-Object -First 1).Trim()
        if (-not $javaExe) { return $null }

        # java.exe 通常在 <JAVA_HOME>\bin\java.exe，向上两级即为 JAVA_HOME
        $binDir  = Split-Path $javaExe -Parent          # ...\bin
        $jdkRoot = Split-Path $binDir  -Parent          # ...\jdk-xx

        # 确认是真正的 JDK（有 bin\javac.exe 才算完整 JDK，jre 没有）
        if (Test-Path (Join-Path $jdkRoot "bin\javac.exe")) {
            return $jdkRoot
        }
        # 只有 JRE 没有 javac，也接受（Maven 只需要 java）
        if (Test-Path (Join-Path $jdkRoot "bin\java.exe")) {
            return $jdkRoot
        }
    } catch {}
    return $null
}

# ─────────────────────────────────────────
# 步骤 2：扫描 Windows 常见安装目录
# ─────────────────────────────────────────
function Find-JavaHomeFromProgramFiles {
    $searchRoots = @(
        "C:\Program Files\Java",
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Microsoft",
        "C:\Program Files\BellSoft",
        "C:\Program Files\Amazon Corretto",
        "C:\Program Files\Zulu",
        "C:\Program Files\OpenJDK",
        "$env:LOCALAPPDATA\Programs\Eclipse Adoptium",
        "$env:USERPROFILE\.jdks"
    )

    $candidates = @()
    foreach ($root in $searchRoots) {
        if (Test-Path $root) {
            Get-ChildItem -Path $root -Directory -ErrorAction SilentlyContinue | ForEach-Object {
                $dir = $_.FullName
                if (Test-Path (Join-Path $dir "bin\java.exe")) {
                    $candidates += $dir
                }
            }
        }
    }

    if ($candidates.Count -eq 0) { return $null }

    # 优先选版本号最高的（按目录名倒序排）
    $best = $candidates | Sort-Object { $_ } -Descending | Select-Object -First 1
    return $best
}

# ─────────────────────────────────────────
# 步骤 3：通过注册表查找（HKLM 软件注册）
# ─────────────────────────────────────────
function Find-JavaHomeFromRegistry {
    $regPaths = @(
        "HKLM:\SOFTWARE\JavaSoft\JDK",
        "HKLM:\SOFTWARE\JavaSoft\Java Development Kit",
        "HKLM:\SOFTWARE\WOW6432Node\JavaSoft\JDK"
    )
    foreach ($regPath in $regPaths) {
        if (Test-Path $regPath) {
            try {
                $currentVersion = (Get-ItemProperty $regPath -ErrorAction Stop).CurrentVersion
                if ($currentVersion) {
                    $versionKey = Join-Path $regPath $currentVersion
                    if (Test-Path $versionKey) {
                        $javaHome = (Get-ItemProperty $versionKey -ErrorAction Stop).JavaHome
                        if ($javaHome -and (Test-Path (Join-Path $javaHome "bin\java.exe"))) {
                            return $javaHome
                        }
                    }
                }
            } catch {}
        }
    }
    return $null
}

# ─────────────────────────────────────────
# 主逻辑：按优先级依次查找
# ─────────────────────────────────────────
$found = $null

Write-Host "  [1/3] 从 PATH 中的 java.exe 反推 JDK 根目录..." -ForegroundColor Yellow
$found = Find-JavaHomeFromPath
if ($found) { Write-Host "        找到：$found" -ForegroundColor Green }

if (-not $found) {
    Write-Host "  [2/3] 扫描 Program Files 常见安装目录..." -ForegroundColor Yellow
    $found = Find-JavaHomeFromProgramFiles
    if ($found) { Write-Host "        找到：$found" -ForegroundColor Green }
}

if (-not $found) {
    Write-Host "  [3/3] 查询注册表..." -ForegroundColor Yellow
    $found = Find-JavaHomeFromRegistry
    if ($found) { Write-Host "        找到：$found" -ForegroundColor Green }
}

# ─────────────────────────────────────────
# 结果处理
# ─────────────────────────────────────────
if ($found) {
    # 注入当前会话的临时环境变量（立即生效，不需要重启终端）
    $env:JAVA_HOME = $found
    $env:PATH      = "$found\bin;" + $env:PATH

    Write-Host ""
    Write-Host "✅ JAVA_HOME 已注入当前会话：" -ForegroundColor Green
    Write-Host "   JAVA_HOME = $env:JAVA_HOME" -ForegroundColor White
    Write-Host ""

    # 验证 java 和 mvn 是否正常
    Write-Host "--- 验证 java 版本 ---" -ForegroundColor Cyan
    try { java -version 2>&1 } catch { Write-Host "java 未找到" -ForegroundColor Red }

    Write-Host ""
    Write-Host "--- 验证 mvn 版本 ---" -ForegroundColor Cyan
    try { mvn --version 2>&1 } catch { Write-Host "mvn 未找到（请确认 Maven 已安装并在 PATH 中）" -ForegroundColor Red }

    Write-Host ""
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
    Write-Host "下一步：在同一终端窗口执行以下命令启动验收：" -ForegroundColor White
    Write-Host ""
    Write-Host "  # 启动 PostgreSQL + MinIO（首次会拉取国内镜像，约1-2分钟）" -ForegroundColor DarkGray
    Write-Host "  docker compose up -d postgres minio" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  # 等待 postgres 健康检查通过后，执行 Maven 测试" -ForegroundColor DarkGray
    Write-Host "  cd backend" -ForegroundColor Yellow
    Write-Host "  mvn clean test" -ForegroundColor Yellow
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan

} else {
    # 未找到 JDK，提供 winget 一键安装命令
    Write-Host ""
    Write-Host "❌ 未能在系统中找到已安装的 JDK。" -ForegroundColor Red
    Write-Host ""
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Yellow
    Write-Host "补救方案：使用 winget 一键安装 Microsoft OpenJDK 21" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  # 步骤 1：安装 JDK 21（约 200MB，需要管理员权限）" -ForegroundColor DarkGray
    Write-Host "  winget install --id Microsoft.OpenJDK.21 --source winget --accept-package-agreements --accept-source-agreements" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  # 步骤 2：安装完成后，关闭并重新打开 PowerShell，再次运行本脚本：" -ForegroundColor DarkGray
    Write-Host "  . .\scripts\fix-java-home.ps1" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  # 或者手动设置（将路径替换为实际安装路径）：" -ForegroundColor DarkGray
    Write-Host '  $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.x.x-hotspot"' -ForegroundColor Yellow
    Write-Host '  $env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH' -ForegroundColor Yellow
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Yellow
}
