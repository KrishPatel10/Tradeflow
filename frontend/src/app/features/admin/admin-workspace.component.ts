import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnInit, Output, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { TradeflowApiService } from '../../core/api/tradeflow-api.service';
import { Account, ApiError, AuditRecord, User } from '../../core/models/tradeflow.models';

@Component({
  selector: 'app-admin-workspace',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './admin-workspace.component.html',
  styleUrl: './admin-workspace.component.scss'
})
export class AdminWorkspaceComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(TradeflowApiService);
  private fundingKey = crypto.randomUUID();

  @Input({ required: true }) user!: User;
  @Output() readonly signedOut = new EventEmitter<void>();

  readonly users = signal<User[]>([]);
  readonly accounts = signal<Account[]>([]);
  readonly auditRecords = signal<AuditRecord[]>([]);
  readonly totalUsers = signal(0);
  readonly totalAccounts = signal(0);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly success = signal('');

  readonly fundingForm = this.fb.nonNullable.group({
    accountId: ['', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    currency: ['INR', [Validators.required, Validators.minLength(3), Validators.maxLength(3)]],
    reference: ['', [Validators.required, Validators.maxLength(100)]]
  });

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    forkJoin({
      users: this.api.adminUsers(),
      accounts: this.api.adminAccounts(),
      audit: this.api.auditRecords()
    }).subscribe({
      next: result => {
        this.users.set(result.users.content);
        this.accounts.set(result.accounts.content);
        this.auditRecords.set(result.audit.content);
        this.totalUsers.set(result.users.totalElements);
        this.totalAccounts.set(result.accounts.totalElements);
        this.loading.set(false);
      },
      error: error => {
        this.showError(error);
        this.loading.set(false);
      }
    });
  }

  selectAccount(): void {
    const account = this.accounts().find(item => item.id === this.fundingForm.controls.accountId.value);
    if (account) this.fundingForm.controls.currency.setValue(account.currency);
  }

  fund(): void {
    if (this.fundingForm.invalid) return;
    const value = this.fundingForm.getRawValue();
    this.busy.set(true);
    this.error.set('');
    this.success.set('');
    this.api.fundAccount(value.accountId, {
      amount: value.amount,
      currency: value.currency.toUpperCase(),
      reference: value.reference.trim()
    }, this.fundingKey).subscribe({
      next: funding => {
        this.success.set(`${funding.amount.toFixed(2)} ${funding.currency} funded successfully.`);
        this.fundingKey = crypto.randomUUID();
        this.fundingForm.controls.amount.setValue(0);
        this.fundingForm.controls.reference.setValue('');
        this.busy.set(false);
        this.refresh();
      },
      error: error => {
        this.showError(error);
        this.busy.set(false);
      }
    });
  }

  owner(userId: string): string {
    const owner = this.users().find(item => item.id === userId);
    return owner ? `${owner.firstName} ${owner.lastName}` : userId.slice(0, 8);
  }

  signOut(): void {
    this.signedOut.emit();
  }

  private showError(response: { error?: ApiError }): void {
    this.error.set(response.error?.message ?? 'The administrator operation could not be completed.');
  }
}
