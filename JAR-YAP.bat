@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
title SportCraft JAR Derleyici
cd /d "%~dp0"

echo ============================================================
echo            SportCraft - Tek TIK JAR Derleyici
echo ============================================================
echo.

REM ---------------------------------------------------------------
REM 1) Gradle wrapper kontrolu
REM ---------------------------------------------------------------
if not exist "gradlew.bat" (
    echo [HATA] gradlew.bat bulunamadi.
    echo Bu betiği projenin kok klasorunde calistir:
    echo icinde build.gradle ve gradlew.bat olan klasor.
    goto :fail
)

REM ---------------------------------------------------------------
REM 2) Makinedeki JDK 17 kurulumunu otomatik bul
REM    (sistemdeki Java 21'i degistirmez, sadece bu oturuma ayarlar)
REM ---------------------------------------------------------------
set "JDK17="
for %%R in (
    "C:\Program Files\Eclipse Adoptium"
    "C:\Program Files\Amazon Corretto"
    "C:\Program Files\Microsoft"
    "C:\Program Files\Zulu"
    "C:\Program Files\BellSoft"
    "C:\Program Files\Java"
) do (
    if exist %%R (
        for /d %%D in (%%R\*17*) do (
            if exist "%%D\bin\java.exe" (
                if not defined JDK17 set "JDK17=%%D"
            )
        )
    )
)

if not defined JDK17 (
    echo [HATA] JDK 17 bulunamadi.
    echo Kurulum icin PowerShell'de:
    echo    winget install EclipseAdoptium.Temurin.17.JDK
    echo Kurulumdan sonra bu pencereyi kapatip betiği tekrar calistir.
    goto :fail
)

set "JAVA_HOME=%JDK17%"
set "PATH=%JDK17%\bin;%PATH%"
echo [BILGI] Kullanilan JDK:
echo    %JDK17%
echo.
"%JDK17%\bin\java.exe" -version
echo.

REM ---------------------------------------------------------------
REM 3) Temiz derleme (onceki hatalı artifact'ler kalmasin)
REM ---------------------------------------------------------------
echo [BILGI] Gradle ile temiz derleme basliyor, ilk calismada birkac
echo        dakika surebilir (bagimliliklar indiriliyor)...
echo.
call gradlew.bat clean build --console=plain --no-daemon
if errorlevel 1 (
    echo.
    echo [HATA] Gradle derlemesi basarisiz oldu. Yukaridaki hata
    echo        satirlarini kontrol et veya ekran goruntusunu paylas.
    goto :fail
)

REM ---------------------------------------------------------------
REM 4) Uretilen JAR'i dogrula
REM ---------------------------------------------------------------
set "JAR="
set "JARNAME="
for %%F in (build\libs\sportcraft-*.jar) do (
    if exist "%%F" (
        set "JAR=%%F"
        set "JARNAME=%%~nxF"
    )
)
if not defined JAR (
    echo [HATA] Derleme bitti ama build\libs altinda sportcraft JAR'i yok.
    goto :fail
)

REM ---------------------------------------------------------------
REM 5) JAR'i ozel klasore kopyala
REM ---------------------------------------------------------------
set "OUT=JAR"
if not exist "%OUT%" mkdir "%OUT%"
copy /y "%JAR%" "%OUT%\" >nul
if errorlevel 1 (
    echo [HATA] JAR '%OUT%' klasorune kopyalanamadi.
    goto :fail
)

echo.
echo ============================================================
echo   BASARILI! JAR hazir:
echo   %CD%\%OUT%\%JARNAME%
echo ============================================================
dir "%OUT%\%JARNAME%"
echo.

REM ---------------------------------------------------------------
REM 6) Varsa .minecraft\mods klasorune de kopyalama secenegi
REM ---------------------------------------------------------------
if exist "%APPDATA%\.minecraft\mods" (
    choice /C EH /M "JAR mods klasorune de kopyalansin mi (E=evet, H=hayir)"
    if errorlevel 2 goto :openfolder
    copy /y "%JAR%" "%APPDATA%\.minecraft\mods\" >nul
    if errorlevel 1 (
        echo [UYARI] mods klasorune kopyalama basarisiz, JAR klasorde duruyor.
    ) else (
        echo [BILGI] Kopyalandi: %APPDATA%\.minecraft\mods
    )
)

:openfolder
explorer "%OUT%"
echo.
echo Pencereyi kapatabilirsin.
pause
exit /b 0

:fail
echo.
echo ISLEM BASARISIZ. Yukaridaki [HATA] satirini oku.
pause
exit /b 1
