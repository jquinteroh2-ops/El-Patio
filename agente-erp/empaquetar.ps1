# Arma la carpeta que se lleva al restaurante: AgenteElPatio.exe con su propio
# Java adentro, así que en ese computador no hay que instalar nada más.
#
#   powershell -ExecutionPolicy Bypass -File empaquetar.ps1
#
# Resultado: dist\AgenteElPatio\  (cópiela entera, con instalar.ps1 y el ejemplo)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

mvn -q package
if ($LASTEXITCODE -ne 0) { throw 'La compilación o las pruebas fallaron' }

$dist = Join-Path $PSScriptRoot 'dist'
if (Test-Path $dist) { Remove-Item $dist -Recurse -Force }
New-Item -ItemType Directory $dist | Out-Null

# El Java que va adentro lleva solo los módulos que el agente usa (red, HTTPS,
# bitácora y lo que pide Jackson). El JDK completo pesaba 150 MB.

# Solo el .jar que se va a empaquetar, sin el original ni las clases sueltas.
$entrada = Join-Path $dist 'entrada'
New-Item -ItemType Directory $entrada | Out-Null
Copy-Item target\agente-erp.jar $entrada

jpackage --type app-image `
  --name AgenteElPatio `
  --input $entrada `
  --main-jar agente-erp.jar `
  --main-class co.elpatio.agente.Agente `
  --app-version 0.1.0 `
  --vendor 'El Patio' `
  --win-console `
  --add-modules 'java.base,java.net.http,java.logging,java.sql,java.xml,jdk.crypto.ec,jdk.charsets,jdk.localedata' `
  --jlink-options '--strip-debug --no-man-pages --no-header-files --compress=zip-6' `
  --dest $dist
if ($LASTEXITCODE -ne 0) { throw 'jpackage falló' }
Remove-Item $entrada -Recurse -Force

Copy-Item agente.properties.ejemplo (Join-Path $dist 'AgenteElPatio')
Copy-Item instalar.ps1 (Join-Path $dist 'AgenteElPatio')
Copy-Item LEEME.md (Join-Path $dist 'AgenteElPatio')

Write-Host "Listo: $dist\AgenteElPatio"
