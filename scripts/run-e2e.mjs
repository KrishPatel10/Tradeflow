import { spawn } from 'node:child_process';
import { resolve } from 'node:path';

const projectName = process.env.TRADEFLOW_E2E_PROJECT ?? 'tradeflow-e2e';
const adminEmail = process.env.TRADEFLOW_E2E_ADMIN_EMAIL ?? 'e2e-admin@tradeflow.test';
const adminPassword = process.env.TRADEFLOW_E2E_ADMIN_PASSWORD ?? 'TradeFlow-E2E-Only-2026!';
const composeArgs = ['compose', '-p', projectName, '--profile', 'app'];
const environment = {
  ...process.env,
  POSTGRES_PORT: process.env.TRADEFLOW_E2E_POSTGRES_PORT ?? '15432',
  REDIS_PORT: process.env.TRADEFLOW_E2E_REDIS_PORT ?? '16380',
  KAFKA_PORT: process.env.TRADEFLOW_E2E_KAFKA_PORT ?? '19092',
  BACKEND_PORT: process.env.TRADEFLOW_E2E_BACKEND_PORT ?? '18080',
  FRONTEND_PORT: process.env.TRADEFLOW_E2E_FRONTEND_PORT ?? '14200',
  TRADEFLOW_ADMIN_EMAIL: adminEmail,
  TRADEFLOW_ADMIN_PASSWORD: adminPassword,
  TRADEFLOW_E2E_ADMIN_EMAIL: adminEmail,
  TRADEFLOW_E2E_ADMIN_PASSWORD: adminPassword,
  TRADEFLOW_E2E_BASE_URL: `http://127.0.0.1:${process.env.TRADEFLOW_E2E_FRONTEND_PORT ?? '14200'}`
};

function run(command, args, options = {}) {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(command, args, { stdio: 'inherit', env: environment, ...options });
    child.on('error', reject);
    child.on('exit', code => code === 0
      ? resolvePromise()
      : reject(new Error(`${command} exited with code ${code}`)));
  });
}

async function waitForApp(url, timeoutMs = 30_000) {
  const deadline = Date.now() + timeoutMs;
  let consecutiveReadyChecks = 0;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`${url}/actuator/health`);
      const body = await response.json();
      consecutiveReadyChecks = response.ok && body.status === 'UP' ? consecutiveReadyChecks + 1 : 0;
      if (consecutiveReadyChecks === 2) return;
    } catch {
      consecutiveReadyChecks = 0;
    }
    await new Promise(resolvePromise => setTimeout(resolvePromise, 500));
  }
  throw new Error(`TradeFlow did not become ready at ${url}`);
}

let primaryError;
try {
  await run('docker', [...composeArgs, 'up', '-d', '--build', '--wait']);
  await waitForApp(environment.TRADEFLOW_E2E_BASE_URL);
  await run(process.execPath, [resolve('node_modules/@playwright/test/cli.js'), 'test']);
} catch (error) {
  primaryError = error;
  process.exitCode = 1;
} finally {
  try {
    await run('docker', [...composeArgs, 'down', '-v', '--remove-orphans']);
  } catch (cleanupError) {
    process.exitCode = 1;
    if (!primaryError) primaryError = cleanupError;
  }
}

if (primaryError) console.error(primaryError);
