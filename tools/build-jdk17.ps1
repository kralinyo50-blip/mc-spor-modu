# SportCraft - JDK 17 derleme yardimcisi
#
# Makinede kurulu JDK 17'yi otomatik bulur, JAVA_HOME'u o oturum icin ayarlar
# ve Gradle wrapper'i dogru Java ile calistirir. Sistemdeki Java 21'i degistirmez.
#
# Kullanim (proje kokunden):
#   powershell -ExecutionPolicy Bypass -File tools\build-jdk17.ps1 build
#   powershell -ExecutionPolicy Bypass -File tools\build-jdk17.ps1 runClient
#   powershell -ExecutionPolicy Bypass -File tools\build-jdk17.ps1 --version

$ErrorActionPreference = 'Stop'

$roots = @(
    "$env:ProgramFiles\Eclipse Adoptium",
    "$env:ProgramFiles\Amazon Corretto",
    "$env:ProgramFiles\Java",
    "$env:ProgramFiles\Microsoft",
    "$env:ProgramFiles\Zulu",
    "$env:ProgramFiles\BellSoft"
)

$jdk17 = $null
foreach ($r in $roots) {
    if (Test-Path $r) {
        Get-ChildItem $r -Directory | ForEach-Object {
            if (-not $jdk17 -and (Test-Path "$($_.FullName)\bin\java.exe")) {
                $versionLine = & "$($_.FullName)\bin\java.exe" -version 2>&1 | Select-Object -First 1
                if ($versionLine -match 'version "17\.') {
                    $script:jdk17 = $_.FullName
                }
            }
        }
    }
}

if (-not $jdk17) {
    Write-Error "JDK 17 bulunamadi. Kurmak icin: winget install EclipseAdoptium.Temurin.17.JDK"
    exit 1
}

$env:JAVA_HOME = $jdk17
$env:PATH = "$jdk17\bin;$env:PATH"
Write-Host "JAVA_HOME = $jdk17" -ForegroundColor Green
& "$PSScriptRoot\..\gradlew.bat" @args
