# 可选：单独发布引擎 zip 到 GitHub Release 时使用。
# Release APK 内置引擎已由 Gradle 任务 packageNativeEnginePacks 自动完成（见 app/build.gradle.kts）。
param(
    [string]$ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path
)

Set-Location $ProjectRoot

function Find-GradleNativeLib {
    param([string]$LibraryName)
    $match = Get-ChildItem "$env:USERPROFILE\.gradle\caches" -Recurse -Filter $LibraryName -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match "arm64-v8a" } |
        Select-Object -First 1
    if ($null -eq $match) {
        throw "Missing library in Gradle cache: $LibraryName"
    }
    return $match.FullName
}

$outputDir = Join-Path $ProjectRoot "build\native-engine-packs"
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

function New-EngineZip {
    param(
        [string]$Name,
        [string[]]$Libraries
    )

    $staging = Join-Path $env:TEMP "xgesture-pack-$Name"
    if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
    $libTarget = Join-Path $staging "lib\arm64-v8a"
    New-Item -ItemType Directory -Force -Path $libTarget | Out-Null

    foreach ($library in $Libraries) {
        $source = Find-GradleNativeLib $library
        Copy-Item $source (Join-Path $libTarget $library)
    }

    $zipPath = Join-Path $outputDir "$Name.zip"
    if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
    Compress-Archive -Path (Join-Path $staging "*") -DestinationPath $zipPath -Force
    Remove-Item $staging -Recurse -Force

    return [PSCustomObject]@{
        Name = $Name
        Path = $zipPath
        Sha256 = (Get-FileHash -Algorithm SHA256 -Path $zipPath).Hash.ToLowerInvariant()
        SizeBytes = (Get-Item $zipPath).Length
    }
}

$ocr = New-EngineZip -Name "ocr-engine-arm64-v6" -Libraries @(
    "libonnxruntime.so",
    "libopencv_java5.so",
    "libleptonica.so",
    "libtesseract.so"
)

$translate = New-EngineZip -Name "translate-engine-arm64-v1" -Libraries @(
    "libtranslate_jni.so",
    "liblanguage_id_l2c_jni.so"
)

Write-Host ""
Write-Host "=== Native engine packs ==="
foreach ($pack in @($ocr, $translate)) {
    Write-Host "$($pack.Name): $([math]::Round($pack.SizeBytes/1MB, 2)) MB"
    Write-Host "  sha256: $($pack.Sha256)"
    Write-Host "  path: $($pack.Path)"
}

$manifestPath = Join-Path $outputDir "pack-manifest.json"
@{
    version = 1
    packs = @(
        @{ id = "ocr-engine"; file = "ocr-engine-arm64-v6.zip"; sha256 = $ocr.Sha256; sizeBytes = $ocr.SizeBytes },
        @{ id = "translate-engine"; file = "translate-engine-arm64-v1.zip"; sha256 = $translate.Sha256; sizeBytes = $translate.SizeBytes }
    )
} | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 $manifestPath

Write-Host ""
Write-Host "Manifest: $manifestPath"
Write-Host "Update core/native-engine/src/main/assets/native_engine_packs.json with sha256 and sizeBytes."
