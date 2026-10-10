# Instala el agente de El Patio en el computador de Globalsoft.
#
# Se corre UNA vez, como administrador, desde la carpeta AgenteElPatio:
#
#   clic derecho en PowerShell -> Ejecutar como administrador
#   cd <carpeta donde copió AgenteElPatio>
#   powershell -ExecutionPolicy Bypass -File instalar.ps1
#
# Qué hace:
#   1. Copia el agente a C:\AgenteElPatio
#   2. Crea agente.properties a partir del ejemplo si no existe, y lo abre
#   3. Hace una pasada de prueba
#   4. Lo deja arrancando solo cada vez que se prende el computador, y
#      reiniciándose solo si se cae
#
# Para quitarlo:  powershell -ExecutionPolicy Bypass -File instalar.ps1 -Desinstalar

param([switch]$Desinstalar)

$ErrorActionPreference = 'Stop'
$destino = 'C:\AgenteElPatio'
$tarea = 'Agente El Patio'

$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
  [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) { throw 'Abra PowerShell como administrador (clic derecho -> Ejecutar como administrador).' }

if ($Desinstalar) {
  Stop-ScheduledTask -TaskName $tarea -ErrorAction SilentlyContinue
  Unregister-ScheduledTask -TaskName $tarea -Confirm:$false -ErrorAction SilentlyContinue
  Write-Host "Se quitó la tarea '$tarea'. La carpeta $destino se deja, con su registro de entregas."
  return
}

# 1. Copiar (sin pisar la configuración ni la memoria si ya estaba instalado)
New-Item -ItemType Directory -Force $destino | Out-Null
Get-ChildItem $PSScriptRoot | Where-Object { $_.Name -notin @('agente.properties', 'datos') } |
  Copy-Item -Destination $destino -Recurse -Force

# 2. Configuración
$config = Join-Path $destino 'agente.properties'
if (-not (Test-Path $config)) {
  Copy-Item (Join-Path $destino 'agente.properties.ejemplo') $config
  Write-Host 'Se abre agente.properties: escriba la llave (nube.llave), guarde y cierre el Bloc de notas.'
  Start-Process notepad.exe $config -Wait
}

# 3. Prueba
$exe = Join-Path $destino 'AgenteElPatio.exe'
& $exe $config --una-vez
if ($LASTEXITCODE -ne 0) {
  throw 'La pasada de prueba falló. Revise el mensaje de arriba y agente.properties, y vuelva a correr este instalador.'
}

# 4. Que arranque solo y se levante si se cae
$accion = New-ScheduledTaskAction -Execute $exe -Argument "`"$config`"" -WorkingDirectory $destino
$disparador = New-ScheduledTaskTrigger -AtStartup
$ajustes = New-ScheduledTaskSettingsSet -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) `
  -ExecutionTimeLimit ([TimeSpan]::Zero) -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
$quien = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
Register-ScheduledTask -TaskName $tarea -Action $accion -Trigger $disparador -Settings $ajustes `
  -Principal $quien -Force | Out-Null
Start-ScheduledTask -TaskName $tarea

Write-Host ''
Write-Host "Instalado. El agente ya está corriendo y arrancará solo con el computador."
Write-Host "Bitácora: $destino\datos\registros\agente-0.log"
