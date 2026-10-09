param(
    [switch]$ConfirmReset
)

$ErrorActionPreference = 'Stop'
$databaseName = 'indexx_demo'

if (-not $ConfirmReset) {
    throw "This drops and recreates only '$databaseName'. Review its contents, then rerun with -ConfirmReset. The existing 'indexx' database is never targeted."
}

$pgBin = if ($env:INDEXX_PG_BIN) { $env:INDEXX_PG_BIN } else { 'C:\Program Files\PostgreSQL\16\bin' }
$dropdb = Join-Path $pgBin 'dropdb.exe'
$createdb = Join-Path $pgBin 'createdb.exe'
if (-not (Test-Path -LiteralPath $dropdb) -or -not (Test-Path -LiteralPath $createdb)) {
    throw "PostgreSQL command-line tools were not found under '$pgBin'. Set INDEXX_PG_BIN to the PostgreSQL bin directory."
}

$pgUser = if ($env:PGUSER) { $env:PGUSER } else { 'postgres' }
& $dropdb --if-exists --host localhost --username $pgUser --password $databaseName
if ($LASTEXITCODE -ne 0) { throw "dropdb failed with exit code $LASTEXITCODE." }
& $createdb --host localhost --username $pgUser --password $databaseName
if ($LASTEXITCODE -ne 0) { throw "createdb failed with exit code $LASTEXITCODE." }

Write-Output "Created empty '$databaseName'. Point DB_URL at jdbc:postgresql://localhost:5432/$databaseName and start the backend once to apply Flyway migrations."
