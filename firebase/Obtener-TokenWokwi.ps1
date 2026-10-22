param(
    [Parameter(Mandatory = $true)]
    [string]$Correo,
    [string]$Configuracion = (Join-Path $PSScriptRoot '..\APP-MOVIL\android\app\google-services.json')
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Configuracion -PathType Leaf)) {
    throw 'Añade el google-services.json de tu propio proyecto o indica su ruta con -Configuracion.'
}
# Esta API key identifica el proyecto. La contraseña y el ID token sí son secretos.
$configuracionFirebase = Get-Content -LiteralPath $Configuracion -Raw | ConvertFrom-Json
$claveProyecto = $configuracionFirebase.client[0].api_key[0].current_key
if (-not $claveProyecto) { throw 'No se encontró la API key en google-services.json.' }

$claveSeguraWokwi = Read-Host 'Contraseña de la cuenta Firebase del ESP32' -AsSecureString
$credencialWokwi = New-Object System.Management.Automation.PSCredential ($Correo, $claveSeguraWokwi)
$cuerpoWokwi = @{
    email = $Correo
    password = $credencialWokwi.GetNetworkCredential().Password
    returnSecureToken = $true
} | ConvertTo-Json -Compress

try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $respuestaWokwi = Invoke-RestMethod -Method Post -TimeoutSec 30 -ContentType 'application/json' `
        -Uri "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$claveProyecto" `
        -Body $cuerpoWokwi
    if (-not $respuestaWokwi.idToken) { throw 'Firebase no devolvió un ID token.' }
    Set-Clipboard -Value $respuestaWokwi.idToken
    Write-Host "Token copiado al portapapeles. UID de la cuenta: $($respuestaWokwi.localId)"
    Write-Host "Vigencia aproximada: $($respuestaWokwi.expiresIn) segundos. Pégalo en el monitor serial de Wokwi."
    Write-Host 'Después de pegarlo puedes vaciar el portapapeles con: Set-Clipboard -Value ""'
} catch {
    # No volcar la respuesta o el cuerpo HTTP: podrían contener credenciales.
    Write-Error 'No se pudo obtener el token. Revisa conexión, proveedor Email/Password, correo, contraseña y restricciones de la API key.'
} finally {
    Remove-Variable claveSeguraWokwi, credencialWokwi, cuerpoWokwi, respuestaWokwi -ErrorAction SilentlyContinue
}
