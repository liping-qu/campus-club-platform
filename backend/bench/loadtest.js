#!/usr/bin/env node
/**
 * 压测客户端 —— 只做一件事：发真实 HTTP 请求，把延迟分布如实算出来。
 *
 * 设计上刻意不引入 autocannon / wrk 这类工具：
 *   1. 它们会给出一堆用不上的指标，我们真正需要的是「这条 SQL / 这次缓存改造
 *      到底让 P95 变了多少」；
 *   2. 参数可控（并发数、总请求数、令牌按 worker 分配），能复现具体场景；
 *   3. 依赖只有 Node 标准库，任何一台机器 clone 下来就能跑。
 *
 * 三个必须说清楚的口径，否则数字没有意义：
 *   - 延迟是「单请求往返时间」，单位毫秒，取 P50/P90/P95/P99/max；
 *   - QPS = 总请求数 / 从第一个请求发出到最后一个响应结束的墙钟时间
 *     （不是「用某个瞬时窗口估算」，也不是把预热算进去）；
 *   - 预热请求不计入统计，但确实会打到服务端 —— 冷启动的类加载、连接建立、
 *     第一轮 SQL 解析都在预热里消化掉，测的是稳态。
 *
 * 用法：
 *   node loadtest.js --mode=get   --url=http://127.0.0.1:8090/api/activities/1 \
 *                    --token=<jwt> --concurrency=32 --requests=6000 [--label=详情]
 *   node loadtest.js --mode=cycle --url=http://127.0.0.1:8090/api/activities/3/registrations \
 *                    --tokens=<jwt1,jwt2,...> --concurrency=8 --requests=2000 [--label=写入]
 */

const http = require('http');
const { URL } = require('url');

// ------------------------------------------------------------------ 参数

function arg(name, fallback) {
  const hit = process.argv.find((item) => item.startsWith(`--${name}=`));
  return hit ? hit.slice(name.length + 3) : fallback;
}

const target = new URL(arg('url', 'http://127.0.0.1:8090/actuator/health'));
const mode = arg('mode', 'get');
const concurrency = Number(arg('concurrency', '32'));
const totalRequests = Number(arg('requests', '2000'));
const label = arg('label', target.pathname);
const warmupRequests = Number(arg('warmup', String(concurrency * 4)));

// 期望的状态码。默认 200；测「不存在的 id」时传 404 ——
// 否则 404 会被 statistics 记成 failure，把「符合预期的业务响应」误报成故障。
const expectedStatuses = new Set(
  arg('expect', '200').split(',').map((item) => Number(item.trim())).filter((n) => !Number.isNaN(n))
);

// get 模式用单个 token；cycle 模式每个 worker 一个（同一用户不能并发报名，会互相冲突）
const singleToken = arg('token', '');
const tokenList = arg('tokens', '') ? arg('tokens', '').split(',').filter(Boolean) : [];
if (mode === 'cycle' && tokenList.length < concurrency) {
  console.error(`cycle 模式需要至少 ${concurrency} 个令牌（每个 worker 一个用户），实际 ${tokenList.length}`);
  process.exit(2);
}

const agent = new http.Agent({ keepAlive: true, maxSockets: concurrency });

// ------------------------------------------------------------------ 单次请求

function send(method, path, token) {
  return new Promise((resolve) => {
    const startedAt = process.hrtime.bigint();
    const req = http.request(
      {
        hostname: target.hostname,
        port: target.port,
        path,
        method,
        agent,
        headers: {
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          ...(method === 'POST' ? { 'Content-Type': 'application/json', 'Content-Length': '2' } : {}),
        },
      },
      (res) => {
        // 必须把响应体读完，否则连接无法复用，压出来的 QPS 反映的是 TCP 建连开销
        res.resume();
        res.on('end', () => {
          resolve({
            status: res.statusCode,
            ms: Number(process.hrtime.bigint() - startedAt) / 1e6,
          });
        });
      }
    );
    req.on('error', (err) => {
      resolve({ status: 0, ms: Number(process.hrtime.bigint() - startedAt) / 1e6, error: err.code });
    });
    req.end(method === 'POST' ? '{}' : undefined);
  });
}

// ------------------------------------------------------------------ 执行

let nextIndex = 0;
const latencies = [];
const statusCounts = new Map();
let failed = 0;
let errorSample = null;

function record(result) {
  statusCounts.set(result.status, (statusCounts.get(result.status) || 0) + 1);
  if (!expectedStatuses.has(result.status)) {
    failed += 1;
    if (!errorSample) errorSample = result;
  }
  latencies.push(result.ms);
}

async function worker(index, limit, collect) {
  const token = mode === 'cycle' ? tokenList[index] : singleToken;
  // cycle 模式：同一个用户交替「报名 / 取消」，一次循环一行，不会出现 409 互相打架
  let registering = true;
  while (true) {
    const slot = nextIndex++;
    if (slot >= limit) return;
    let result;
    if (mode === 'cycle') {
      result = registering
        ? await send('POST', target.pathname, token)
        : await send('DELETE', `${target.pathname}/me`, token);
      registering = !registering;
    } else {
      result = await send('GET', target.pathname + target.search, token);
    }
    if (collect) record(result);
  }
}

async function runRound(limit, collect) {
  nextIndex = 0;
  const workers = [];
  for (let i = 0; i < concurrency; i++) workers.push(worker(i, limit, collect));
  const startedAt = Date.now();
  await Promise.all(workers);
  return Date.now() - startedAt;
}

function percentile(sorted, p) {
  if (sorted.length === 0) return 0;
  // 用线性插值，不是简单取整下标：样本少的时候取整会让 P99 退化成 max
  const rank = (p / 100) * (sorted.length - 1);
  const low = Math.floor(rank);
  const high = Math.ceil(rank);
  if (low === high) return sorted[low];
  return sorted[low] + (sorted[high] - sorted[low]) * (rank - low);
}

(async () => {
  // 预热：把 JIT、连接池、缓存都跑热，避免把冷启动算进稳态指标
  await runRound(warmupRequests, false);

  const latencyStart = process.hrtime.bigint();
  await runRound(totalRequests, true);
  const wallMs = Number(process.hrtime.bigint() - latencyStart) / 1e6;

  latencies.sort((a, b) => a - b);
  const qps = (totalRequests / wallMs) * 1000;
  const statusText = [...statusCounts.entries()].sort((a, b) => b[1] - a[1])
    .map(([code, count]) => `${code || 'ERR'}×${count}`).join(' ');

  console.log(`\n### ${label}`);
  console.log(`请求数 ${totalRequests}  并发 ${concurrency}  耗时 ${(wallMs / 1000).toFixed(2)}s`);
  console.log(`QPS ${qps.toFixed(0)}  非预期响应 ${failed}  状态码 ${statusText}`);
  console.log(`P50 ${percentile(latencies, 50).toFixed(2)}ms  `
    + `P90 ${percentile(latencies, 90).toFixed(2)}ms  `
    + `P95 ${percentile(latencies, 95).toFixed(2)}ms  `
    + `P99 ${percentile(latencies, 99).toFixed(2)}ms  `
    + `max ${latencies[latencies.length - 1].toFixed(2)}ms`);
  if (errorSample) {
    console.log(`首个失败样本：status=${errorSample.status} error=${errorSample.error || '-'}`);
  }
  agent.destroy();
})();
