import { Injectable, computed, signal } from '@angular/core';
import { TokenResponse } from '../models/tradeflow.models';

const TOKEN_KEY = 'tradeflow.tokens';

@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly tokens = signal<TokenResponse | null>(this.restore());
  readonly authenticated = computed(() => this.tokens() !== null);
  readonly accessToken = computed(() => this.tokens()?.accessToken ?? null);
  readonly userId = computed(() => this.subject(this.tokens()?.accessToken));

  set(tokens: TokenResponse): void {
    localStorage.setItem(TOKEN_KEY, JSON.stringify(tokens));
    this.tokens.set(tokens);
  }

  clear(): void {
    localStorage.removeItem(TOKEN_KEY);
    this.tokens.set(null);
  }

  private restore(): TokenResponse | null {
    try {
      const value = localStorage.getItem(TOKEN_KEY);
      return value ? (JSON.parse(value) as TokenResponse) : null;
    } catch {
      return null;
    }
  }

  private subject(token?: string): string | null {
    try {
      if (!token) return null;
      const raw = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      const payload = raw.padEnd(raw.length + ((4 - raw.length % 4) % 4), '=');
      return (JSON.parse(atob(payload)) as { sub: string }).sub;
    } catch {
      return null;
    }
  }
}
