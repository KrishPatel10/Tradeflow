import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { TradeflowApiService } from '../../core/api/tradeflow-api.service';
import { User } from '../../core/models/tradeflow.models';
import { AdminWorkspaceComponent } from './admin-workspace.component';

describe('AdminWorkspaceComponent', () => {
  let fixture: ComponentFixture<AdminWorkspaceComponent>;
  const admin: User = {
    id: '10000000-0000-0000-0000-000000000001',
    email: 'admin@tradeflow.local',
    firstName: 'TradeFlow',
    lastName: 'Administrator',
    role: 'ADMIN',
    status: 'ACTIVE',
    createdAt: '2026-09-13T00:00:00Z',
    updatedAt: '2026-09-13T00:00:00Z'
  };
  const account = {
    id: '20000000-0000-0000-0000-000000000001',
    userId: '30000000-0000-0000-0000-000000000001',
    accountNumber: '1000000000000001',
    currency: 'INR',
    balance: 125,
    status: 'ACTIVE',
    version: 0,
    createdAt: '2026-09-13T00:00:00Z',
    updatedAt: '2026-09-13T00:00:00Z'
  };
  const customer: User = {
    ...admin,
    id: account.userId,
    email: 'customer@example.com',
    firstName: 'Asha',
    lastName: 'Rao',
    role: 'CUSTOMER'
  };
  const api = {
    adminUsers: vi.fn(() => of({ content: [admin, customer], page: 0, size: 100, totalElements: 2, totalPages: 1 })),
    adminAccounts: vi.fn(() => of({ content: [account], page: 0, size: 100, totalElements: 1, totalPages: 1 })),
    auditRecords: vi.fn(() => of({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })),
    fundAccount: vi.fn(() => of({
      id: '40000000-0000-0000-0000-000000000001', accountId: account.id, amount: 50,
      currency: 'INR', reference: 'deposit-42', actorId: admin.id, createdAt: '2026-09-13T00:00:00Z'
    }))
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminWorkspaceComponent],
      providers: [{ provide: TradeflowApiService, useValue: api }]
    }).compileComponents();
    fixture = TestBed.createComponent(AdminWorkspaceComponent);
    fixture.componentRef.setInput('user', admin);
    fixture.detectChanges();
  });

  it('renders administrator account inventory', () => {
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('h1')?.textContent).toContain('Money operations');
    expect(page.textContent).toContain('Asha Rao');
    expect(page.textContent).toContain('₹125.00');
  });

  it('submits a validated funding operation', () => {
    fixture.componentInstance.fundingForm.setValue({
      accountId: account.id,
      amount: 50,
      currency: 'INR',
      reference: 'deposit-42'
    });
    fixture.componentInstance.fund();

    expect(api.fundAccount).toHaveBeenCalledWith(account.id,
      { amount: 50, currency: 'INR', reference: 'deposit-42' }, expect.any(String));
    expect(fixture.componentInstance.success()).toContain('funded successfully');
  });
});
