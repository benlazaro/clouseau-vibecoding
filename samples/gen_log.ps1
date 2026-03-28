$loggers = @(
    'com.example.app.Bootstrap','com.example.app.Watchdog',
    'com.example.db.DataSourceFactory','com.example.db.OrderRepository','com.example.db.ProductRepository',
    'com.example.web.RequestFilter','com.example.web.ProductController','com.example.web.OrderController',
    'com.example.service.OrderService','com.example.service.PaymentService',
    'com.example.service.InventoryService','com.example.service.ReportService',
    'com.example.infra.security.AuthManager','com.example.infra.cache.RedisConnector',
    'com.example.infra.cache.LocalCache','com.example.infra.net.TcpReceiver',
    'com.example.infra.messaging.EventBus','com.example.infra.jobs.MetricsCollector',
    'com.example.infra.jobs.HealthChecker'
)
$threads = @('main','http-nio-8443-exec-1','http-nio-8443-exec-2','http-nio-8443-exec-3',
             'http-nio-8443-exec-4','http-nio-8443-exec-5','scheduler-1','io-worker-1',
             'io-worker-2','eviction-thread','watchdog')
$levels = (@('TRACE') * 15) + (@('DEBUG') * 30) + (@('INFO ') * 30) + (@('WARN ') * 15) + (@('ERROR') * 8) + (@('FATAL') * 2)

$msgTrace = @(
    'Entering method processOrder() with args [orderId={0}, customerId={1}]',
    'Cache lookup for key=product:{0} hit={1}',
    'Raw packet dump (hex): 48 54 54 50 2F 31 2E 31 20 32 30 30 20 4F 4B 0D 0A',
    'Frame decoded: type=0x02 flags=0x04 streamId={0} payloadLen={1}',
    'SQL bind params: [id={0}, status=PENDING, limit={1}]',
    'Checksum bytes: 0xDE 0xAD 0xBE 0xEF 0xCA 0xFE 0xBA 0xBE seq={0}'
)
$msgDebug = @(
    'Processing request id={0} from client 10.0.{1}.{2}',
    'DB query executed in {0}ms: SELECT * FROM orders WHERE customer_id=?',
    'Cache hit ratio: {0}% (hits={1}, misses={2})',
    'Connection pool stats: active={0} idle={1} waiting={2}',
    'JWT claims: sub=user-{0} roles=[USER] exp=1780000000',
    'Evicting {0} stale entries from local cache (policy=LRU)',
    'Payment gateway response: status=AUTHORISED authCode=A{0}C processingTimeMs={1}',
    'Health check: database=UP responseMs={0} redis=UP responseMs={1}'
)
$msgInfo = @(
    'Order ORD-20260327-{0} created successfully for customer CUST-{1}',
    'Payment authorised for order ORD-20260327-{0} authCode=A{1}F',
    'User user-{0} logged in from 10.0.{1}.{2}',
    'Batch processed: {0} records in {1}ms',
    'Cache warmed up: {0} entries loaded in {1}ms',
    'Audit: user=admin@example.com action=UPDATE resource=order/ORD-20260327-{0}',
    'Server accepted connection from 10.0.{0}.{1}:{2} total-active={3}'
)
$msgWarn = @(
    'Slow query detected ({0}ms > threshold 200ms): SELECT * FROM products WHERE name ILIKE ?',
    'Rate limit approaching for client 10.0.{0}.{1}: {2}/100 requests',
    'Heap usage high: {0}% used={1}MB max=1024MB',
    'Connection pool utilisation at {0}% ({1}/20 active)',
    'Redis node redis-0{0}:6379 did not respond within 250ms retry={1}/3'
)
$msgError = @(
    'Failed to process order ORD-20260327-{0}: upstream returned HTTP {1}',
    'Database query failed after {0}ms on connection pool slot {1}',
    'Connection to db-replica-{0}:5432 refused after 3 retries',
    'Transaction rolled back for order ORD-20260327-{0}: unique constraint violation'
)
$msgFatal = @(
    'Unrecoverable error in database subsystem: all {0} connections lost',
    'Database connection pool collapsed FATAL: too many connections current={0}/200'
)
$msgMap = @{
    'TRACE' = $msgTrace; 'DEBUG' = $msgDebug; 'INFO ' = $msgInfo
    'WARN ' = $msgWarn;  'ERROR' = $msgError; 'FATAL' = $msgFatal
}

$stackTrace = @(
    'java.sql.SQLException: Connection to db-primary:5432 refused',
    "`tat org.postgresql.core.v3.ConnectionFactoryImpl.openConnectionImpl(ConnectionFactoryImpl.java:497)",
    "`tat org.postgresql.core.ConnectionFactory.openConnection(ConnectionFactory.java:49)",
    "`tat com.zaxxer.hikari.pool.PoolBase.newConnection(PoolBase.java:358)",
    "`tat com.example.db.DataSourceFactory.reconnect(DataSourceFactory.java:387)",
    "`tat com.example.service.OrderService.processQueue(OrderService.java:304)",
    "`tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)",
    "`tat java.lang.Thread.run(Thread.java:748)"
)

$target  = 100MB
$rng     = [System.Random]::new(42)
$ts      = [datetime]::new(2026,3,27,8,0,0)
$written = 0
$outFile = 'C:\Users\benla\git\clouseau-project\large_sample.log'
$sw      = [System.IO.StreamWriter]::new($outFile, $false, [System.Text.Encoding]::UTF8, 1MB)

try {
    while ($written -lt $target) {
        $ts     = $ts.AddMilliseconds(1)
        $level  = $levels[$rng.Next($levels.Count)]
        $logger = $loggers[$rng.Next($loggers.Count)]
        $thread = $threads[$rng.Next($threads.Count)]
        $msgs   = $msgMap[$level]
        $tmpl   = $msgs[$rng.Next($msgs.Count)]
        $a      = @($rng.Next(1,9999), $rng.Next(1,9999), $rng.Next(1,9999), $rng.Next(1,9999))
        try   { $msg = $tmpl -f $a[0],$a[1],$a[2],$a[3] }
        catch { $msg = $tmpl }
        $line = '{0:yyyy-MM-dd HH:mm:ss.fff} [{1}] {2} {3} - {4}' -f $ts,$thread,$level,$logger,$msg
        $sw.WriteLine($line)
        $written += $line.Length + 2
        if (($level -eq 'ERROR' -or $level -eq 'FATAL') -and $rng.NextDouble() -lt 0.4) {
            foreach ($st in $stackTrace) { $sw.WriteLine($st); $written += $st.Length + 2 }
        }
    }
} finally {
    $sw.Close()
}

$mb = (Get-Item $outFile).Length / 1MB
Write-Host ('Done: {0:F1} MB' -f $mb)
