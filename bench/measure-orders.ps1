# Order query index benchmark (no app; runs SQL directly against MySQL)
#   Usage: powershell -ExecutionPolicy Bypass -File .\measure-orders.ps1
#   Options: -Sets A,B  -Repeat 21  -KeepContainer
# Works on Windows PowerShell 5.1. ASCII only on purpose (5.1 reads BOM-less scripts as ANSI).

param(
    [string[]]$Sets = @('A', 'B', 'C'),
    [int]$Repeat = 21,
    [switch]$KeepContainer,
    [string]$Name = 'idx-bench-ps'
)

$ErrorActionPreference = 'Continue'
$Image = 'mysql:8.0.33'
$Pw = 'bench'

# Set definitions: 20 orders per user on average, only the user count grows
$SetDefs = @{
    'A' = @{ Users = 1000;  Orders = 20000 }
    'B' = @{ Users = 5000;  Orders = 100000 }
    'C' = @{ Users = 25000; Orders = 500000 }
}

$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$OutDir = Join-Path $PSScriptRoot "results-ps\$Stamp"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$Inv = [Globalization.CultureInfo]::InvariantCulture

# ---------- helpers ----------

# Runs SQL through stdin. Mode: raw (-N -B -r), batch (-B with header), table (-t)
function Invoke-Sql {
    param([string]$Sql, [string]$Mode = 'raw')
    $opts = @('exec', '-i', '-e', "MYSQL_PWD=$Pw", $Name, 'mysql', '-uroot', 'bench')
    if ($Mode -eq 'raw') { $opts += @('-N', '-B', '-r') }
    if ($Mode -eq 'batch') { $opts += @('-B') }
    if ($Mode -eq 'table') { $opts += @('-t') }
    $out = $Sql | & docker @opts
    if ($LASTEXITCODE -ne 0) { throw "SQL failed: $($Sql.Substring(0, [Math]::Min(200, $Sql.Length)))" }
    return $out
}

function Get-Median([double[]]$xs) {
    $s = $xs | Sort-Object
    $n = $s.Count
    if ($n % 2 -eq 1) { return $s[($n - 1) / 2] }
    return ($s[$n / 2 - 1] + $s[$n / 2]) / 2
}

# EXPLAIN ANALYZE x Repeat in one session; drop the first; median of the top node's "actual time=a..b" b value
function Measure-Query([string]$q) {
    $sb = New-Object System.Text.StringBuilder
    for ($i = 0; $i -lt $Repeat; $i++) { [void]$sb.AppendLine("EXPLAIN ANALYZE $q;") }
    $lines = Invoke-Sql $sb.ToString() 'raw'
    $times = @()
    foreach ($l in $lines) {
        if ($l -match '^-> .*?actual time=[\d.]+\.\.([\d.]+)') {
            $times += [double]::Parse($Matches[1], $Inv)
        }
    }
    if ($times.Count -lt 2) { throw "Could not parse EXPLAIN ANALYZE for: $q" }
    return Get-Median ($times | Select-Object -Skip 1)
}

# Traditional EXPLAIN: key / rows / Extra of the orders row
function Get-Plan([string]$q) {
    $lines = @(Invoke-Sql "EXPLAIN $q;" 'batch')
    $hdr = $lines[0] -split "`t"
    $pick = $null
    foreach ($l in ($lines | Select-Object -Skip 1)) {
        $c = $l -split "`t"
        $t = $c[[array]::IndexOf($hdr, 'table')]
        if ($t -eq 'o' -or $t -eq 'orders') { $pick = $c; break }
        if ($pick -eq $null) { $pick = $c }
    }
    return @{
        Key   = $pick[[array]::IndexOf($hdr, 'key')]
        Rows  = $pick[[array]::IndexOf($hdr, 'rows')]
        Extra = $pick[[array]::IndexOf($hdr, 'Extra')]
    }
}

function Wait-MySql {
    # The entrypoint first runs a temp server with networking off, so wait until TCP works
    for ($i = 0; $i -lt 90; $i++) {
        cmd /c "docker exec -e MYSQL_PWD=$Pw $Name mysql -uroot -h127.0.0.1 -e ""SELECT 1"" >nul 2>nul"
        if ($LASTEXITCODE -eq 0) { return }
        Start-Sleep -Seconds 2
    }
    throw 'MySQL did not become ready'
}

function Get-DataSql([int]$users, [int]$orders) {
    # 3 years = 94,608,000 s, created_at grows with order_id
    return @"
SET SESSION cte_max_recursion_depth = 1000000;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS user;
CREATE TABLE user (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255),
    customer_payment_key VARCHAR(255),
    role VARCHAR(255),
    name VARCHAR(255),
    phone VARCHAR(255),
    address VARCHAR(255),
    address_detail VARCHAR(255),
    email VARCHAR(255),
    created_at DATETIME(6), created_by VARCHAR(255),
    updated_at DATETIME(6), updated_by VARCHAR(255)
) ENGINE=InnoDB;
CREATE TABLE orders (
    order_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(255) NOT NULL UNIQUE,
    payment_key VARCHAR(255) UNIQUE,
    user_id BIGINT,
    final_price INT NOT NULL,
    order_name VARCHAR(255),
    receiver_name VARCHAR(255),
    receiver_address VARCHAR(255),
    order_address_detail VARCHAR(255),
    receiver_phone VARCHAR(255),
    request_note VARCHAR(255),
    payment_type VARCHAR(255),
    order_status VARCHAR(255),
    order_type VARCHAR(255),
    approved_at DATETIME(6),
    created_at DATETIME(6), created_by VARCHAR(255),
    updated_at DATETIME(6), updated_by VARCHAR(255),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES user (user_id)
) ENGINE=InnoDB;

INSERT INTO user (username, customer_payment_key, role, name, phone, address, address_detail, email,
                  created_at, created_by, updated_at, updated_by)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < $users)
SELECT CONCAT('user', n), CONCAT('ck_', n), 'ROLE_USER', CONCAT('name', n), CONCAT('010', LPAD(n, 8, '0')),
       'Teheran-ro 123, Gangnam-gu, Seoul', 'Apt 101-1001', CONCAT('user', n, '@example.com'),
       '2022-12-01', 'system', '2022-12-01', 'system'
FROM seq;

INSERT INTO orders (order_number, payment_key, user_id, final_price, order_name, receiver_name,
                    receiver_address, order_address_detail, receiver_phone, request_note,
                    payment_type, order_status, order_type, approved_at,
                    created_at, created_by, updated_at, updated_by)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < $orders)
SELECT CONCAT('ORD', LPAD(n, 12, '0')), CONCAT('pk_', n), uid,
       1000 + (n * 37 % 200) * 500, CONCAT('Product ', n % 500, ' and ', n % 3, ' more'), 'Hong Gildong',
       'Teheran-ro 123, Gangnam-gu, Seoul', 'Apt 101-1001', '010-1234-5678', 'Leave at the door',
       'CARD',
       CASE WHEN sr < 85 THEN 'PAID' WHEN sr < 95 THEN 'DELIVERED' ELSE 'CANCELED' END,
       'CART', ts, ts, CONCAT('user', uid), ts, CONCAT('user', uid)
FROM (SELECT n,
             1 + CRC32(CONCAT('u', n)) % $users AS uid,
             CRC32(CONCAT('s', n)) % 100 AS sr,
             TIMESTAMP('2023-01-01') + INTERVAL FLOOR(n * 94608000 / $orders) SECOND AS ts
      FROM seq) s;

ANALYZE TABLE user, orders;
"@
}

# ---------- run ----------

$results = @()
try {
    Write-Host "[setup] starting $Image ($Name)"
    cmd /c "docker rm -f $Name >nul 2>nul"
    & docker run -d --name $Name --cpus=2 --memory=1g -e "MYSQL_ROOT_PASSWORD=$Pw" -e 'MYSQL_DATABASE=bench' `
        $Image --innodb-buffer-pool-size=512M | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'docker run failed' }
    Wait-MySql

    foreach ($set in $Sets) {
        $def = $SetDefs[$set]
        Write-Host ""
        Write-Host "[set $set] users=$($def.Users) orders=$($def.Orders): loading data"
        $t0 = Get-Date
        Invoke-Sql (Get-DataSql $def.Users $def.Orders) 'raw' | Out-Null
        Write-Host ("          loaded in {0:N0}s" -f ((Get-Date) - $t0).TotalSeconds)

        # Median user by order count, and the 30-day boundary as a literal (so MAX() cost is not measured)
        $userCount = [int](Invoke-Sql 'SELECT COUNT(DISTINCT user_id) FROM orders;')
        $off = [Math]::Floor(($userCount - 1) / 2)
        $med = (Invoke-Sql "SELECT user_id, c FROM (SELECT user_id, COUNT(*) c FROM orders GROUP BY user_id) t ORDER BY c, user_id LIMIT 1 OFFSET $off;") -split "`t"
        $since = Invoke-Sql "SELECT DATE_FORMAT(MAX(created_at) - INTERVAL 30 DAY, '%Y-%m-%d %H:%i:%s') FROM orders;"
        Write-Host "          median user: user_id=$($med[0]) ($($med[1]) orders), last-30-days since $since"

        $admin = 'SELECT o.*, u.* FROM orders o JOIN user u ON u.user_id = o.user_id'
        $queries = [ordered]@{
            '1 user orders'      = "SELECT o.order_id, o.order_number, o.created_at, o.order_status, o.final_price FROM orders o WHERE o.user_id = $($med[0]) ORDER BY o.created_at DESC LIMIT 0, 10"
            '2 admin list'       = "$admin ORDER BY o.created_at DESC LIMIT 0, 20"
            '3 admin last 30d'   = "$admin WHERE o.created_at >= '$since' ORDER BY o.created_at DESC LIMIT 0, 20"
            '4 admin keyword'    = "$admin WHERE o.order_number LIKE '%12345%' ORDER BY o.created_at DESC LIMIT 0, 20"
            '5 admin COUNT'      = 'SELECT COUNT(*) FROM orders'
            '6 admin OFFSET 10k' = "$admin ORDER BY o.created_at DESC LIMIT 10000, 20"
        }

        foreach ($phase in @('before', 'after')) {
            if ($phase -eq 'after') {
                Write-Host "          creating indexes"
                Invoke-Sql 'CREATE INDEX idx_order_user_create ON orders (user_id, created_at); CREATE INDEX idx_order_created ON orders (created_at); ANALYZE TABLE orders;' 'raw' | Out-Null
            }
            foreach ($qn in $queries.Keys) {
                $q = $queries[$qn]
                $ms = Measure-Query $q
                $plan = Get-Plan $q
                $results += [pscustomobject]@{
                    Set = $set; Query = $qn; Phase = $phase; Ms = $ms
                    Key = $plan.Key; Rows = $plan.Rows; Extra = $plan.Extra
                }
                Write-Host ("          {0,-6} {1,-20} {2,10:N3} ms  key={3} rows={4}" -f $phase, $qn, $ms, $plan.Key, $plan.Rows)

                # Evidence for slides: set C, admin list and user orders, before/after
                if ($set -eq 'C' -and ($qn -eq '1 user orders' -or $qn -eq '2 admin list')) {
                    $tag = ($qn -replace '^\d ', '' -replace ' ', '-')
                    $file = Join-Path $OutDir "evidence-C-$tag-$phase.txt"
                    $text = @("-- $q", '', '-- EXPLAIN') + (Invoke-Sql "EXPLAIN $q;" 'table') +
                            @('', '-- EXPLAIN ANALYZE') + (Invoke-Sql "EXPLAIN ANALYZE $q;" 'raw')
                    $text | Set-Content -Path $file -Encoding UTF8
                }
            }
        }
    }
}
finally {
    if (-not $KeepContainer) {
        Write-Host ""
        Write-Host "[cleanup] removing container $Name"
        cmd /c "docker rm -f $Name >nul 2>nul"
    }
}

# ---------- summary ----------

$summary = @()
foreach ($qn in ($results | Select-Object -ExpandProperty Query -Unique)) {
    foreach ($set in $Sets) {
        $b = $results | Where-Object { $_.Set -eq $set -and $_.Query -eq $qn -and $_.Phase -eq 'before' }
        $a = $results | Where-Object { $_.Set -eq $set -and $_.Query -eq $qn -and $_.Phase -eq 'after' }
        if ($b -eq $null -or $a -eq $null) { continue }
        $summary += [pscustomobject]@{
            Query       = $qn
            Set         = "$set ($($SetDefs[$set].Orders))"
            'Before ms' = [Math]::Round($b.Ms, 3)
            'After ms'  = [Math]::Round($a.Ms, 3)
            'Key'       = "$($b.Key) -> $($a.Key)"
            'Rows'      = "$($b.Rows) -> $($a.Rows)"
            'Extra'     = "$($b.Extra) -> $($a.Extra)"
        }
    }
}

Write-Host ""
$summary | Format-Table -AutoSize | Out-String -Width 300 | Write-Host

$results | Export-Csv -Path (Join-Path $OutDir 'raw.csv') -NoTypeInformation -Encoding UTF8
$md = @("# Order index benchmark ($Stamp)", '',
        "mysql $Image, --cpus=2 --memory=1g, innodb_buffer_pool_size=512M, EXPLAIN ANALYZE x $Repeat (first dropped, median ms)", '',
        '| Query | Set (orders) | Before ms | After ms | key | rows | Extra |',
        '|---|---|---|---|---|---|---|')
foreach ($r in $summary) {
    $md += "| $($r.Query) | $($r.Set) | $($r.'Before ms') | $($r.'After ms') | $($r.Key) | $($r.Rows) | $($r.Extra) |"
}
$md | Set-Content -Path (Join-Path $OutDir 'summary.md') -Encoding UTF8
Write-Host "results: $OutDir"
