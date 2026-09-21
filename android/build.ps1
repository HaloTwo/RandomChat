$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$jdk = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { Join-Path $env:USERPROFILE 'Documents\Unity\6000.3.14f1\Editor\Data\PlaybackEngines\AndroidPlayer\OpenJDK' }
$env:JAVA_HOME = $jdk
$tools = Join-Path $sdk 'build-tools\33.0.0'
$platform = Join-Path $sdk 'platforms\android-33\android.jar'
$output = Join-Path $root 'build'
foreach ($required in @((Join-Path $jdk 'bin\javac.exe'), (Join-Path $tools 'aapt2.exe'), $platform)) {
    if (!(Test-Path -LiteralPath $required)) { throw "Android 빌드 도구를 찾을 수 없습니다: $required" }
}
New-Item -ItemType Directory -Force -Path (Join-Path $output 'classes'), (Join-Path $output 'dex'), (Join-Path $output 'compiled') | Out-Null
& (Join-Path $jdk 'bin\javac.exe') -encoding UTF-8 -source 8 -target 8 -classpath $platform -d (Join-Path $output 'classes') (Join-Path $root 'src\com\moment\randomchat\MainActivity.java')
if ($LASTEXITCODE -ne 0) { throw 'Java 컴파일 실패' }
& (Join-Path $tools 'aapt2.exe') compile --dir (Join-Path $root 'res') -o (Join-Path $output 'compiled')
if ($LASTEXITCODE -ne 0) { throw 'Android 리소스 컴파일 실패' }
$resources = @(Get-ChildItem -LiteralPath (Join-Path $output 'compiled') -Filter '*.flat' | ForEach-Object FullName)
& (Join-Path $tools 'aapt2.exe') link -o (Join-Path $output 'unsigned.apk') -I $platform --manifest (Join-Path $root 'AndroidManifest.xml') $resources
if ($LASTEXITCODE -ne 0) { throw 'Android 리소스 링크 실패' }
$classes = @(Get-ChildItem -LiteralPath (Join-Path $output 'classes') -Recurse -Filter '*.class' | ForEach-Object FullName)
& (Join-Path $jdk 'bin\java.exe') -cp (Join-Path $tools 'lib\d8.jar') com.android.tools.r8.D8 --lib $platform --output (Join-Path $output 'dex') $classes
if ($LASTEXITCODE -ne 0) { throw 'DEX 변환 실패' }
& (Join-Path $jdk 'bin\jar.exe') uf (Join-Path $output 'unsigned.apk') -C (Join-Path $output 'dex') classes.dex
if ($LASTEXITCODE -ne 0) { throw 'APK 묶기 실패' }
& (Join-Path $tools 'zipalign.exe') -f 4 (Join-Path $output 'unsigned.apk') (Join-Path $output 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'APK 정렬 실패' }
$debugKey = Join-Path $env:USERPROFILE '.android\debug.keystore'
if (!(Test-Path -LiteralPath $debugKey)) { throw "기본 Android 디버그 키를 찾을 수 없습니다: $debugKey" }
& (Join-Path $tools 'apksigner.bat') sign --ks $debugKey --ks-key-alias androiddebugkey --ks-pass 'pass:android' --key-pass 'pass:android' --out (Join-Path $output 'moment-local-debug.apk') (Join-Path $output 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'APK 서명 실패' }
& (Join-Path $tools 'apksigner.bat') verify (Join-Path $output 'moment-local-debug.apk')
if ($LASTEXITCODE -ne 0) { throw 'APK 서명 검증 실패' }
Write-Output (Join-Path $output 'moment-local-debug.apk')
