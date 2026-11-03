# Verify release APK versionCode/versionName match app/build.gradle.kts.
param(
    [string]$ApkPath = "",
    [ValidateSet("full", "lite", "all", "auto")]
    [string]$Variant = "all",
    [string]$GradleFile = "app/build.gradle.kts"
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$GradleFile = Join-Path $ProjectRoot $GradleFile

if (-not (Test-Path $GradleFile)) {
    throw "Gradle file not found: $GradleFile"
}

$gradleText = Get-Content $GradleFile -Raw
if ($gradleText -notmatch 'versionCode\s*=\s*(\d+)') {
    throw "Failed to parse versionCode from $GradleFile"
}
$expectedCode = $Matches[1]
if ($gradleText -notmatch 'versionName\s*=\s*"([^"]+)"') {
    throw "Failed to parse versionName from $GradleFile"
}
$expectedName = $Matches[1]

function Get-ApkVariant {
    param([string]$Path)
    if ($Path -like "*-lite.apk") { return "lite" }
    if ($Path -like "*-full.apk") { return "full" }
    return "unknown"
}

function Verify-ReleaseApk {
    param(
        [string]$Path,
        [string]$ResolvedVariant
    )

    if (-not (Test-Path $Path)) {
        throw "APK not found: $Path"
    }

    if ($ResolvedVariant -eq "auto") {
        $ResolvedVariant = Get-ApkVariant $Path
    }
    if ($ResolvedVariant -ne "full" -and $ResolvedVariant -ne "lite") {
        throw "Unknown APK variant for: $Path (expected -full.apk or -lite.apk suffix)"
    }

    Write-Host "==> Verifying $ResolvedVariant APK: $Path"

    $aapt = @(
        "$env:ANDROID_SDK_ROOT\build-tools\*\aapt.exe",
        "$env:ANDROID_HOME\build-tools\*\aapt.exe",
        "$env:LOCALAPPDATA\Android\Sdk\build-tools\*\aapt.exe"
    ) | ForEach-Object { Get-Item $_ -ErrorAction SilentlyContinue } |
        Sort-Object { $_.Directory.Name } -Descending |
        Select-Object -First 1

    if (-not $aapt) {
        throw "aapt not found. Install Android build-tools or set ANDROID_SDK_ROOT."
    }

    $badgingText = (& $aapt.FullName dump badging $Path 2>&1 | Out-String)
    if ($badgingText -notmatch "versionCode='(\d+)'") {
        throw "Failed to parse versionCode from APK"
    }
    $actualCode = $Matches[1]
    if ($badgingText -notmatch "versionName='([^']+)'") {
        throw "Failed to parse versionName from APK"
    }
    $actualName = $Matches[1]

    Write-Host "Expected: versionCode=$expectedCode versionName=$expectedName"
    Write-Host "Actual:   versionCode=$actualCode versionName=$actualName"

    if ($actualCode -ne $expectedCode -or $actualName -ne $expectedName) {
        throw "Release APK version mismatch."
    }

    Write-Host "OK: Release APK version matches $GradleFile"

    $apkBytes = (Get-Item $Path).Length
    Write-Host "APK size: $apkBytes bytes"

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $requiredPacks = @("ocr-engine", "translate-engine")
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        if ($ResolvedVariant -eq "full") {
            foreach ($pack in $requiredPacks) {
                $assetPath = "assets/bundled-native-engine/$pack.zip"
                if (-not $zip.GetEntry($assetPath)) {
                    throw "Missing bundled asset in full APK: $assetPath"
                }
                Write-Host "OK: Found $assetPath"
            }
            Write-Host "OK: Full release APK bundled native engine assets verified"
            $minApkBytes = if ($env:MIN_RELEASE_FULL_APK_BYTES) { [long]$env:MIN_RELEASE_FULL_APK_BYTES } else { 35000000 }
            if ($apkBytes -lt $minApkBytes) {
                throw "Full release APK too small (min $minApkBytes bytes); bundled native engine packs may be missing or empty."
            }
        } else {
            foreach ($pack in $requiredPacks) {
                $assetPath = "assets/bundled-native-engine/$pack.zip"
                if ($zip.GetEntry($assetPath)) {
                    throw "Lite release APK must not bundle native engine asset: $assetPath"
                }
            }
            Write-Host "OK: Lite release APK has no bundled native engine assets"
            $maxApkBytes = if ($env:MAX_RELEASE_LITE_APK_BYTES) { [long]$env:MAX_RELEASE_LITE_APK_BYTES } else { 36700160 } # 35 MiB（2026-10-20 由 32 MiB 上调：图标库三个字体使 lite 实测 33,648,752 B）
            if ($apkBytes -gt $maxApkBytes) {
                throw "Lite release APK too large (max $maxApkBytes bytes)."
            }
        }
    } finally {
        $zip.Dispose()
    }
}

if (-not [string]::IsNullOrWhiteSpace($ApkPath)) {
    Verify-ReleaseApk -Path (Join-Path $ProjectRoot $ApkPath) -ResolvedVariant $Variant
    exit 0
}

if ($Variant -eq "all" -or $Variant -eq "full") {
    Verify-ReleaseApk -Path (Join-Path $ProjectRoot "app/build/outputs/apk/full/release/xgesture-$expectedName-full.apk") -ResolvedVariant "full"
}
if ($Variant -eq "all" -or $Variant -eq "lite") {
    Verify-ReleaseApk -Path (Join-Path $ProjectRoot "app/build/outputs/apk/lite/release/xgesture-$expectedName-lite.apk") -ResolvedVariant "lite"
}
