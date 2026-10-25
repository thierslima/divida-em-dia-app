@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
TITLE Dívida em Dia - Gerar APK

echo =====================================================
echo              DÍVIDA EM DIA - GERAR APK
echo              Desenvolvido por Thiers Lima
echo =====================================================
echo.

if "%JAVA_HOME%"=="" (
  if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
)
if "%JAVA_HOME%"=="" (
  echo [ERRO] Java nao encontrado.
  echo Instale o Android Studio e execute novamente.
  echo https://developer.android.com/studio
  pause
  exit /b 1
)

set "SDK=%ANDROID_HOME%"
if "%SDK%"=="" set "SDK=%LOCALAPPDATA%\Android\Sdk"
if not exist "%SDK%\platforms\android-35\android.jar" (
  echo [ERRO] Android SDK 35 nao encontrado em:
  echo %SDK%
  echo.
  echo No Android Studio, abra SDK Manager e instale Android SDK Platform 35.
  pause
  exit /b 1
)

set "SDKPROP=%SDK:\=/%"
>local.properties echo sdk.dir=%SDKPROP%

set "GRADLE_DIR=%~dp0.tools\gradle-8.9"
set "GRADLE_ZIP=%~dp0.tools\gradle-8.9-bin.zip"
if not exist "%GRADLE_DIR%\bin\gradle.bat" (
  echo Baixando Gradle 8.9 pela primeira vez...
  if not exist "%~dp0.tools" mkdir "%~dp0.tools"
  curl -L --fail --retry 3 -o "%GRADLE_ZIP%" https://services.gradle.org/distributions/gradle-8.9-bin.zip
  if errorlevel 1 (
    echo [ERRO] Nao foi possivel baixar o Gradle.
    pause
    exit /b 1
  )
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%GRADLE_ZIP%' -DestinationPath '%~dp0.tools' -Force"
)

echo.
echo Compilando o aplicativo...
call "%GRADLE_DIR%\bin\gradle.bat" --no-daemon assembleDebug
if errorlevel 1 (
  echo.
  echo [ERRO] A compilacao falhou. Leia a mensagem acima.
  pause
  exit /b 1
)

set "APK=%~dp0app\build\outputs\apk\debug\app-debug.apk"
if exist "%APK%" (
  copy /Y "%APK%" "%~dp0DividaEmDia-v1.0.apk" >nul
  echo.
  echo =====================================================
  echo APK GERADO COM SUCESSO!
  echo Arquivo: DividaEmDia-v1.0.apk
  echo =====================================================
) else (
  echo [ERRO] APK nao encontrado apos a compilacao.
)

echo.
pause
