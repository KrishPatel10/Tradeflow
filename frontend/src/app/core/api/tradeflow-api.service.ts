import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from './api.config';
import { Account, AccountFunding, AuditRecord, Notification, PageResponse, TokenResponse, Transfer, User } from '../models/tradeflow.models';

@Injectable({ providedIn: 'root' })
export class TradeflowApiService {
  constructor(private readonly http: HttpClient) {}

  register(payload: { email: string; firstName: string; lastName: string; password: string }) {
    return this.http.post<User>(`${API_BASE_URL}/users`, payload);
  }

  login(email: string, password: string) {
    return this.http.post<TokenResponse>(`${API_BASE_URL}/auth/login`, { email, password });
  }

  user(id: string) {
    return this.http.get<User>(`${API_BASE_URL}/users/${id}`);
  }

  accounts(userId: string) {
    return this.http.get<PageResponse<Account>>(`${API_BASE_URL}/users/${userId}/accounts`);
  }

  createAccount(userId: string, currency: string) {
    return this.http.post<Account>(`${API_BASE_URL}/accounts`, { userId, currency });
  }

  adminUsers() {
    return this.http.get<PageResponse<User>>(`${API_BASE_URL}/admin/users?size=100`);
  }

  adminAccounts() {
    return this.http.get<PageResponse<Account>>(`${API_BASE_URL}/admin/accounts?size=100`);
  }

  auditRecords() {
    return this.http.get<PageResponse<AuditRecord>>(`${API_BASE_URL}/admin/audit-records?size=10`);
  }

  fundAccount(accountId: string, payload: { amount: number; currency: string; reference: string }, idempotencyKey: string) {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http.post<AccountFunding>(`${API_BASE_URL}/admin/accounts/${accountId}/fundings`, payload, { headers });
  }

  transfer(payload: { fromAccountId: string; toAccountId: string; amount: number; currency: string }, idempotencyKey: string) {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http.post<Transfer>(`${API_BASE_URL}/transfers`, payload, { headers });
  }

  history(accountId: string) {
    return this.http.get<PageResponse<Transfer>>(`${API_BASE_URL}/accounts/${accountId}/transfers`);
  }

  notifications(userId: string) {
    return this.http.get<PageResponse<Notification>>(`${API_BASE_URL}/users/${userId}/notifications`);
  }
}
