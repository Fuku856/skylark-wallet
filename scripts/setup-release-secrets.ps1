<#
.SYNOPSIS
    リリース用の署名 keystore を用意し、GitHub Actions の Secret に登録する。

.DESCRIPTION
    .github/workflows/release.yml が APK の署名に使う次の Secret を登録する。
      KEYSTORE_BASE64 / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD

    -KeystorePath の keystore があればそれを使い、なければこのアプリ専用の keystore を新しく作る。
    パスワードは実行中に入力する（画面には表示しない）。値はコマンドラインや一時ファイルに残さず、
    gh の標準入力に直接渡す。

    前提: GitHub CLI（gh）でログイン済みであること。JDK または Android Studio（keytool）が入っていること。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\setup-release-secrets.ps1

.EXAMPLE
    # 作成済みの keystore を使う
    powershell -ExecutionPolicy Bypass -File scripts\setup-release-secrets.ps1 -KeystorePath D:\keys\skylark.jks -KeyAlias skylark

.EXAMPLE
    # 登録はせず、keystore の確認・作成までを試す
    powershell -ExecutionPolicy Bypass -File scripts\setup-release-secrets.ps1 -WhatIf
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    # keystore のパス。リポジトリの外に置く（失うとアプリをアップデートできなくなる）
    [string]$KeystorePath = (Join-Path (Join-Path $HOME 'keystores') 'skylark-wallet-release.jks'),

    # 鍵のエイリアス
    [string]$KeyAlias = 'skylark',

    # Secret を登録するリポジトリ
    [string]$Repo = 'Fuku856/skylark-wallet',

    # 既に登録されている Secret を確認なしで上書きする
    [switch]$Force,

    # テスト用。指定すると入力を求めない
    [Parameter(DontShow = $true)]
    [System.Security.SecureString]$StorePassword
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$PasswordEnvName = 'SKYLARK_KEYSTORE_PASSWORD'

function Write-Step([string]$Message) {
    Write-Host ''
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Stop-WithError([string]$Message) {
    Write-Host ''
    Write-Host "エラー: $Message" -ForegroundColor Red
    exit 1
}

# 外部コマンドを実行し、終了コードと出力（標準出力・標準エラー）を返す
function Invoke-Native([string]$FilePath, [string[]]$Arguments) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue' # 標準エラーへの出力で止まらないようにする
    try {
        $output = & $FilePath @Arguments 2>&1 | ForEach-Object { "$_" }
        return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = ($output -join [Environment]::NewLine) }
    } finally {
        $ErrorActionPreference = $previous
    }
}

function ConvertTo-PlainText([System.Security.SecureString]$Secure) {
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

function Find-Keytool {
    $command = Get-Command keytool -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command) { return $command.Source }

    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += Join-Path $env:JAVA_HOME 'bin' }
    if ($env:ProgramFiles) { $candidates += Join-Path $env:ProgramFiles 'Android\Android Studio\jbr\bin' }
    if ($env:LOCALAPPDATA) { $candidates += Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr\bin' }
    foreach ($dir in $candidates) {
        foreach ($name in @('keytool.exe', 'keytool')) {
            $path = Join-Path $dir $name
            if (Test-Path -LiteralPath $path -PathType Leaf) { return $path }
        }
    }
    return $null
}

# 値を gh の標準入力に渡して Secret を登録する（コマンドラインや一時ファイルに値を残さない）
function Set-GhSecret([string]$GhPath, [string]$Name, [string]$Value) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $GhPath
    $psi.Arguments = "secret set $Name --repo $Repo"
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true

    $process = [System.Diagnostics.Process]::Start($psi)
    # 文字コードの差を避けるため、UTF-8（BOM なし・末尾の改行なし）のバイト列で書き込む
    $bytes = (New-Object System.Text.UTF8Encoding($false)).GetBytes($Value)
    $process.StandardInput.BaseStream.Write($bytes, 0, $bytes.Length)
    $process.StandardInput.Close()
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        Stop-WithError "$Name の登録に失敗しました。`n$stderr$stdout"
    }
    Write-Host "  $Name を登録しました" -ForegroundColor Green
}

# --- 事前チェック ---------------------------------------------------------------

Write-Step 'GitHub CLI を確認しています'
$gh = Get-Command gh -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $gh) {
    Stop-WithError "GitHub CLI（gh）が見つかりません。https://cli.github.com/ からインストールし、'gh auth login' でログインしてください。"
}
$ghPath = $gh.Source
$auth = Invoke-Native $ghPath @('auth', 'status')
if ($auth.ExitCode -ne 0) {
    Stop-WithError "GitHub にログインしていません。'gh auth login' を実行してから、もう一度実行してください。"
}
Write-Host "  OK（登録先: $Repo）"

Write-Step 'keytool を確認しています'
$keytool = Find-Keytool
if (-not $keytool) {
    Stop-WithError 'keytool が見つかりません。JDK をインストールして JAVA_HOME を設定するか、Android Studio をインストールしてください。'
}
Write-Host "  $keytool"

# --- keystore -----------------------------------------------------------------

$isNew = -not (Test-Path -LiteralPath $KeystorePath -PathType Leaf)
$password = $null

if ($isNew) {
    Write-Step "keystore を新しく作ります: $KeystorePath"
    if ($StorePassword) {
        $password = ConvertTo-PlainText $StorePassword
    } else {
        Write-Host '  keystore のパスワードを決めてください（6文字以上）。'
        Write-Host '  このパスワードと keystore のファイルを失うと、アプリをアップデートできなくなります。' -ForegroundColor Yellow
        $first = ConvertTo-PlainText (Read-Host -Prompt '  パスワード' -AsSecureString)
        $second = ConvertTo-PlainText (Read-Host -Prompt '  パスワード（確認）' -AsSecureString)
        if ($first -cne $second) { Stop-WithError 'パスワードが一致しません。' }
        $password = $first
    }
    if ($password.Length -lt 6) { Stop-WithError 'パスワードは6文字以上にしてください。' }
    # PKCS12 の keystore は ASCII 以外のパスワードを扱えない
    if ($password -notmatch '^[\x21-\x7E]+$') {
        Stop-WithError 'パスワードには半角の英数字と記号だけを使ってください（空白・全角文字は使えません）。'
    }
} else {
    Write-Step "既存の keystore を使います: $KeystorePath"
    if ($StorePassword) {
        $password = ConvertTo-PlainText $StorePassword
    } else {
        $password = ConvertTo-PlainText (Read-Host -Prompt '  keystore のパスワード' -AsSecureString)
    }
}

# パスワードは環境変数経由で keytool に渡し、コマンドラインに平文を出さない。
# -WhatIf の影響を受けないよう（値が表示されないよう）、.NET の API で設定する
[Environment]::SetEnvironmentVariable($PasswordEnvName, $password, 'Process')
try {
    if ($isNew) {
        $dir = Split-Path -Parent $KeystorePath
        if ($dir -and -not (Test-Path -LiteralPath $dir)) {
            New-Item -ItemType Directory -Path $dir -Force -WhatIf:$false | Out-Null
        }
        # PKCS12 では鍵のパスワードを keystore のパスワードと同じにする
        $result = Invoke-Native $keytool @(
            '-genkeypair', '-keystore', $KeystorePath, '-storetype', 'PKCS12',
            '-alias', $KeyAlias, '-keyalg', 'RSA', '-keysize', '4096', '-validity', '10000',
            '-dname', 'CN=Skylark Wallet',
            "-storepass:env", $PasswordEnvName, "-keypass:env", $PasswordEnvName
        )
        if ($result.ExitCode -ne 0) { Stop-WithError "keystore を作成できませんでした。`n$($result.Output)" }
        Write-Host '  作成しました' -ForegroundColor Green
    }

    # パスワードとエイリアスが正しいか確かめる
    $result = Invoke-Native $keytool @(
        '-list', '-keystore', $KeystorePath, '-alias', $KeyAlias, "-storepass:env", $PasswordEnvName
    )
    if ($result.ExitCode -ne 0) {
        Stop-WithError "keystore を開けません。パスワードまたはエイリアス（$KeyAlias）が違います。`n$($result.Output)"
    }
    Write-Host "  パスワードとエイリアス（$KeyAlias）を確認しました"
} finally {
    [Environment]::SetEnvironmentVariable($PasswordEnvName, $null, 'Process')
}

# --- Secret の登録 --------------------------------------------------------------

$secrets = [ordered]@{
    KEYSTORE_BASE64   = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $KeystorePath).Path))
    KEYSTORE_PASSWORD = $password
    KEY_ALIAS         = $KeyAlias
    KEY_PASSWORD      = $password
}

Write-Step "GitHub Secret を登録します（$Repo）"
$list = Invoke-Native $ghPath @('secret', 'list', '--repo', $Repo, '--json', 'name', '--jq', '.[].name')
if ($list.ExitCode -ne 0) {
    Stop-WithError "Secret の一覧を取得できません。リポジトリへの管理者権限があるか確認してください。`n$($list.Output)"
}
$existing = @($list.Output -split '\r?\n' | Where-Object { $secrets.Contains($_) })
if ($existing.Count -gt 0 -and -not $Force -and -not $WhatIfPreference) {
    Write-Host "  次の Secret は登録済みです: $($existing -join ', ')" -ForegroundColor Yellow
    $answer = Read-Host '  上書きしますか？ (y/N)'
    if ($answer -notmatch '^[yY]') {
        Write-Host '  中止しました。'
        exit 0
    }
}

foreach ($name in $secrets.Keys) {
    if ($PSCmdlet.ShouldProcess("$Repo の Secret $name", '登録')) {
        Set-GhSecret $ghPath $name $secrets[$name]
    }
}

Write-Step '完了'
if (-not $WhatIfPreference) {
    (Invoke-Native $ghPath @('secret', 'list', '--repo', $Repo)).Output | Write-Host
}
Write-Host ''
Write-Host "keystore（$KeystorePath）とパスワードは、必ず安全な場所にバックアップしてください。" -ForegroundColor Yellow
Write-Host '失うと、同じアプリとしてアップデートできなくなります。' -ForegroundColor Yellow
