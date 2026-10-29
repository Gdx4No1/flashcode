function Initialize-FlashcodeLocalSecret {
    param([Parameter(Mandatory)][string]$RuntimeRoot)
    $absolute = [System.IO.Path]::GetFullPath($RuntimeRoot)
    if ($absolute -notmatch '^[Dd]:[\\/].+') { throw 'Runtime directory must be a specific path on D:' }
    New-Item -ItemType Directory -Path $absolute -Force | Out-Null
    $secretPath = Join-Path $absolute 'jwt-secret.local'
    if (!(Test-Path -LiteralPath $secretPath)) {
        $bytes = New-Object byte[] 64
        [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
        $content = [System.Text.Encoding]::ASCII.GetBytes([Convert]::ToBase64String($bytes))
        $stream = [System.IO.File]::Open($secretPath, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
        try { $stream.Write($content, 0, $content.Length) } finally { $stream.Dispose() }
    }
    $value = [System.IO.File]::ReadAllText($secretPath).Trim()
    try { if ([Convert]::FromBase64String($value).Length -lt 64) { throw 'Invalid length' } }
    catch { throw 'Invalid private JWT key; fix the D: runtime file without exposing it' }
    $env:FLASHCODE_JWT_SECRET = $value
}
