export interface TokenResponse {
  tokenType: string;
  accessToken: string;
  expiresIn: number;
  refreshToken: string;
}

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: 'CUSTOMER' | 'ADMIN';
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface Account {
  id: string;
  userId: string;
  accountNumber: string;
  currency: string;
  balance: number;
  status: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface AccountFunding {
  id: string;
  accountId: string;
  amount: number;
  currency: string;
  reference: string;
  actorId: string;
  createdAt: string;
}

export interface AuditRecord {
  id: string;
  entityType: string;
  entityId: string;
  action: string;
  previousState: string | null;
  newState: string | null;
  actorId: string | null;
  createdAt: string;
}

export interface Transfer {
  id: string;
  fromAccountId: string;
  toAccountId: string;
  amount: number;
  currency: string;
  status: string;
  completedAt: string;
}

export interface Notification {
  id: string;
  transferId: string;
  message: string;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiError {
  code: string;
  message: string;
}
