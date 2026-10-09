# Bank Manager - dağıtım klasörünü hazırlar (dist\BankManager)
# Kullanım: proje klasöründe  powershell -ExecutionPolicy Bypass -File installer\build.ps1
# Sonra kurulum dosyası için:  ISCC installer\BankManager.iss
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$version = "1.0.0"
$jar = "bank-management-$version.jar"

Write-Host "1/4 Testler ve derleme..."
mvn -B -q clean package
if ($LASTEXITCODE -ne 0) { throw "Maven derlemesi başarısız" }

Write-Host "2/4 Bağımlılıklar kopyalanıyor..."
mvn -B -q dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target\libs
if ($LASTEXITCODE -ne 0) { throw "Bağımlılıklar kopyalanamadı" }
Copy-Item "target\$jar" "target\libs\"

Write-Host "3/4 Gerekli Java modülleri hesaplanıyor..."
# Sadece gereken JDK modülleri paketlenir; tüm JDK'yı gömmekten çok daha küçük
$modules = jdeps --ignore-missing-deps --multi-release 21 --print-module-deps --class-path "target\libs\*" "target\libs\$jar"
if ($LASTEXITCODE -ne 0) { throw "jdeps başarısız" }
$modules = "$modules,jdk.localedata"   # Türkçe tarih ve ay adları (jlink sadece tr ve en yerel ayarlarını alır)
Write-Host "   Modüller: $modules"

Write-Host "4/4 jpackage ile uygulama klasörü oluşturuluyor..."
if (Test-Path dist) { Remove-Item -Recurse -Force dist }
jpackage --type app-image `
    --name BankManager `
    --app-version $version `
    --vendor "Miraç Deprem" `
    --description "Bank Manager" `
    --icon installer\icon.ico `
    --input target\libs `
    --main-jar $jar `
    --main-class com.mrcdprm.bank.Launcher `
    --add-modules $modules `
    --jlink-options "--strip-debug --no-header-files --no-man-pages --include-locales=en,tr" `
    --dest dist
if ($LASTEXITCODE -ne 0) { throw "jpackage başarısız" }

$size = (Get-ChildItem dist\BankManager -Recurse | Measure-Object Length -Sum).Sum / 1MB
Write-Host ("Hazır: dist\BankManager ({0:N1} MB)" -f $size)
