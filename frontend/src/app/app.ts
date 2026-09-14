import { CommonModule } from '@angular/common';
import { Component, HostListener, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { switchMap } from 'rxjs';
import { TradeflowApiService } from './core/api/tradeflow-api.service';
import { AuthStore } from './core/auth/auth.store';
import { Account, ApiError, Notification, Transfer, User } from './core/models/tradeflow.models';
import { AdminWorkspaceComponent } from './features/admin/admin-workspace.component';

type WorkspaceSection = 'overview' | 'accounts' | 'transfer' | 'activity';

@Component({
  selector: 'app-root',
  imports: [CommonModule, ReactiveFormsModule, AdminWorkspaceComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(TradeflowApiService);
  private transferKey = crypto.randomUUID();
  readonly auth = inject(AuthStore);

  readonly mode = signal<'login' | 'register'>('login');
  readonly busy = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly user = signal<User | null>(null);
  readonly accounts = signal<Account[]>([]);
  readonly transfers = signal<Transfer[]>([]);
  readonly notifications = signal<Notification[]>([]);
  readonly activeSection = signal<WorkspaceSection>('overview');
  readonly selectedHistoryAccountId = signal('');

  readonly loginForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });
  readonly registerForm = this.fb.nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]]
  });
  readonly accountForm = this.fb.nonNullable.group({
    currency: ['INR', [Validators.required, Validators.minLength(3), Validators.maxLength(3)]]
  });
  readonly transferForm = this.fb.nonNullable.group({
    fromAccountId: ['', Validators.required],
    toAccountId: ['', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    currency: ['INR', Validators.required]
  });

  ngOnInit(): void {
    this.syncSectionFromHash();
    if (this.auth.authenticated()) this.loadDashboard();
  }

  @HostListener('window:hashchange')
  @HostListener('window:popstate')
  syncSectionFromHash(): void {
    const section = globalThis.location?.hash.slice(1) as WorkspaceSection;
    if (['overview', 'accounts', 'transfer', 'activity'].includes(section)) {
      this.activeSection.set(section);
    } else {
      this.activeSection.set('overview');
    }
  }

  selectSection(section: WorkspaceSection, event: Event): void {
    event.preventDefault();
    this.activeSection.set(section);
    globalThis.history?.pushState(null, '', `#${section}`);
    if (section === 'activity') {
      const accountId = this.selectedHistoryAccountId() || this.accounts()[0]?.id;
      if (accountId) this.loadHistory(accountId);
    }
  }

  login(): void {
    if (this.loginForm.invalid) return;
    const value = this.loginForm.getRawValue();
    this.run(() => this.api.login(value.email, value.password).subscribe({
      next: tokens => { this.auth.set(tokens); this.loadDashboard(); }, error: error => this.showError(error)
    }));
  }

  register(): void {
    if (this.registerForm.invalid) return;
    const value = this.registerForm.getRawValue();
    this.run(() => this.api.register(value).pipe(switchMap(() => this.api.login(value.email, value.password)))
      .subscribe({ next: tokens => { this.auth.set(tokens); this.loadDashboard(); }, error: error => this.showError(error) }));
  }

  createAccount(): void {
    const userId = this.auth.userId();
    if (!userId || this.accountForm.invalid) return;
    const value = this.accountForm.getRawValue();
    this.run(() => this.api.createAccount(userId, value.currency.toUpperCase()).subscribe({
      next: () => { this.success.set('Account opened with a zero balance.'); this.loadAccounts(); },
      error: error => this.showError(error)
    }));
  }

  sendTransfer(): void {
    if (this.transferForm.invalid) return;
    const value = this.transferForm.getRawValue();
    if (value.fromAccountId === value.toAccountId) { this.error.set('Choose two different accounts.'); return; }
    this.run(() => this.api.transfer({ ...value, currency: value.currency.toUpperCase() }, this.transferKey).subscribe({
      next: transfer => {
        this.success.set(`Transfer ${transfer.id.slice(0, 8)} completed.`);
        this.transferKey = crypto.randomUUID();
        this.loadAccounts(); this.loadHistory(value.fromAccountId);
      },
      error: error => this.showError(error)
    }));
  }

  loadHistory(accountId: string): void {
    this.selectedHistoryAccountId.set(accountId);
    this.api.history(accountId).subscribe({ next: page => this.transfers.set(page.content), error: error => this.showError(error) });
  }

  loadSelectedHistory(event: Event): void {
    const accountId = (event.target as HTMLSelectElement).value;
    if (accountId) this.loadHistory(accountId);
  }

  logout(): void {
    this.auth.clear(); this.user.set(null); this.accounts.set([]); this.transfers.set([]); this.notifications.set([]); this.selectedHistoryAccountId.set('');
  }

  private loadDashboard(): void {
    const userId = this.auth.userId();
    if (!userId) { this.logout(); return; }
    this.api.user(userId).subscribe({
      next: user => {
        this.user.set(user);
        if (user.role === 'CUSTOMER') {
          this.loadAccounts();
          this.api.notifications(userId).subscribe({ next: page => this.notifications.set(page.content), error: () => undefined });
        }
      },
      error: error => this.showError(error)
    });
  }

  private loadAccounts(): void {
    const userId = this.auth.userId();
    if (!userId) return;
    this.api.accounts(userId).subscribe({ next: page => {
      this.accounts.set(page.content);
      if (page.content.length && !this.transferForm.controls.fromAccountId.value) {
        this.transferForm.controls.fromAccountId.setValue(page.content[0].id);
      }
      const selectedAccountId = page.content.some(account => account.id === this.selectedHistoryAccountId())
        ? this.selectedHistoryAccountId()
        : page.content[0]?.id ?? '';
      this.selectedHistoryAccountId.set(selectedAccountId);
      if (this.activeSection() === 'activity' && selectedAccountId) this.loadHistory(selectedAccountId);
    }, error: error => this.showError(error) });
  }

  private run(start: () => { add(teardown: () => void): void }): void {
    this.busy.set(true); this.error.set(''); this.success.set('');
    start().add(() => this.busy.set(false));
  }

  private showError(response: { error?: ApiError }): void {
    this.error.set(response.error?.message ?? 'Something went wrong. Please try again.');
  }
}
