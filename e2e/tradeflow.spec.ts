import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const password = 'FlowPass!2026';

type Account = {
  id: string;
  accountNumber: string;
  balance: number;
  currency: string;
};

type PageResponse<T> = { content: T[] };

async function register(page: Page, email: string): Promise<void> {
  await page.goto('/');
  await page.getByRole('button', { name: 'New to TradeFlow? Create an account' }).click();
  await page.getByLabel('First name').fill('Browser');
  await page.getByLabel('Last name').fill('Tester');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('heading', { name: 'Welcome back, Browser.' })).toBeVisible();
}

async function login(page: Page, email: string, loginPassword: string): Promise<void> {
  await page.goto('/');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(loginPassword);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
}

async function accessToken(page: Page): Promise<string> {
  return page.evaluate(() => {
    const raw = localStorage.getItem('tradeflow.tokens');
    if (!raw) throw new Error('TradeFlow access token is missing');
    return (JSON.parse(raw) as { accessToken: string }).accessToken;
  });
}

async function accounts(request: APIRequestContext, token: string): Promise<Account[]> {
  const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString()) as { sub: string };
  const response = await request.get(`/api/users/${payload.sub}/accounts`, {
    headers: { Authorization: `Bearer ${token}` }
  });
  expect(response.ok()).toBeTruthy();
  return (await response.json() as PageResponse<Account>).content;
}

test('customer registration opens a zero-balance account and preserves admin boundaries', async ({ page, request }) => {
  const email = `browser-basic-${Date.now()}@tradeflow.test`;
  await register(page, email);

  await expect(page.getByText('No accounts yet.')).toBeVisible();
  await page.getByRole('button', { name: 'Open account' }).click();
  await expect(page.getByText('Account opened with a zero balance.')).toBeVisible();
  await expect(page.getByText('INR account')).toBeVisible();
  const token = await accessToken(page);
  const customerAccounts = await accounts(request, token);
  expect(customerAccounts).toHaveLength(1);
  expect(Number(customerAccounts[0].balance)).toBe(0);

  await page.getByRole('link', { name: 'Accounts', exact: true }).click();
  await expect(page).toHaveURL(/#accounts$/);
  await expect(page.getByRole('heading', { name: 'Your accounts' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'New transfer' })).toBeHidden();

  await page.getByRole('link', { name: 'New transfer', exact: true }).click();
  await expect(page).toHaveURL(/#transfer$/);
  await expect(page.getByRole('heading', { name: 'New transfer' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Your accounts' })).toBeHidden();

  await page.getByRole('link', { name: 'Activity', exact: true }).click();
  await expect(page).toHaveURL(/#activity$/);
  await expect(page.getByRole('heading', { name: 'Recent transfers' })).toBeVisible();
  await expect(page.getByRole('combobox', { name: 'Transaction account' })).toHaveValue(customerAccounts[0].id);
  await expect(page.getByRole('heading', { name: 'Notifications' })).toBeVisible();

  const forbidden = await request.get('/api/admin/accounts', {
    headers: { Authorization: `Bearer ${token}` }
  });
  expect(forbidden.status()).toBe(403);
});

test('administrator funding enables an atomic transfer and Kafka notification', async ({ page, request }) => {
  const adminEmail = process.env['TRADEFLOW_E2E_ADMIN_EMAIL'];
  const adminPassword = process.env['TRADEFLOW_E2E_ADMIN_PASSWORD'];
  test.skip(!adminEmail || !adminPassword, 'Set the E2E admin credentials or run pnpm e2e:stack.');

  const email = `browser-flow-${Date.now()}@tradeflow.test`;
  await register(page, email);
  await page.getByRole('button', { name: 'Open account' }).click();
  await expect(page.getByText('Account opened with a zero balance.')).toBeVisible();
  await page.getByRole('button', { name: 'Open account' }).click();

  const customerToken = await accessToken(page);
  await expect.poll(async () => (await accounts(request, customerToken)).length).toBe(2);
  const customerAccounts = await accounts(request, customerToken);
  const fundedAccount = customerAccounts[0];
  const destinationAccount = customerAccounts[1];

  await page.getByRole('button', { name: 'Sign out' }).click();
  await login(page, adminEmail!, adminPassword!);
  await expect(page.getByRole('heading', { name: 'Money operations' })).toBeVisible();

  await page.getByLabel('Destination account').selectOption(fundedAccount.id);
  await page.getByLabel('Amount').fill('250');
  await page.getByLabel('Verified reference').fill(`PW-${Date.now()}`);
  await page.getByRole('button', { name: 'Record funding' }).click();
  await expect(page.getByText('250.00 INR funded successfully.')).toBeVisible();

  await page.getByRole('button', { name: 'Sign out' }).click();
  await login(page, email, password);
  await expect(page.getByRole('heading', { name: 'Welcome back, Browser.' })).toBeVisible();

  await page.getByLabel('From').selectOption(fundedAccount.id);
  await page.getByLabel('To').selectOption(destinationAccount.id);
  await page.getByLabel('Amount').fill('75');

  const retryKeys: string[] = [];
  let responseDropped = false;
  await page.route('**/api/transfers', async route => {
    retryKeys.push(route.request().headers()['idempotency-key']);
    if (!responseDropped) {
      responseDropped = true;
      const response = await route.fetch();
      expect(response.ok()).toBeTruthy();
      await route.abort('failed');
      return;
    }
    await route.continue();
  });

  await page.getByRole('button', { name: 'Send securely' }).click();
  await expect(page.getByText('Something went wrong. Please try again.')).toBeVisible();
  await page.getByRole('button', { name: 'Send securely' }).click();
  await expect(page.getByText(/Transfer [a-f0-9]{8} completed\./)).toBeVisible();
  await page.unroute('**/api/transfers');
  expect(retryKeys).toHaveLength(2);
  expect(new Set(retryKeys).size).toBe(1);

  await expect.poll(async () => {
    const response = await request.get('/api/users/' + JSON.parse(Buffer.from(customerToken.split('.')[1], 'base64url').toString()).sub + '/notifications', {
      headers: { Authorization: `Bearer ${customerToken}` }
    });
    const body = await response.json() as PageResponse<{ message: string }>;
    return body.content.some(notification => notification.message.includes('75.00 INR'));
  }, { timeout: 20_000 }).toBeTruthy();

  const finalAccounts = await accounts(request, customerToken);
  expect(Number(finalAccounts.find(account => account.id === fundedAccount.id)?.balance)).toBe(175);
  expect(Number(finalAccounts.find(account => account.id === destinationAccount.id)?.balance)).toBe(75);

  await page.reload();
  await expect(page.getByText(/75\.00 INR/)).toBeVisible();
});
