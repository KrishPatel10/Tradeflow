import { TestBed } from '@angular/core/testing';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
    }).compileComponents();
  });

  afterEach(() => globalThis.history.replaceState(null, '', '/'));

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render the TradeFlow authentication experience', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.brand')?.textContent).toContain('TradeFlow');
    expect(compiled.querySelector('h1')?.textContent).toContain('Move money with confidence');
  });

  it('tracks the selected customer workspace tab', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const event = new Event('click', { cancelable: true });

    app.selectSection('accounts', event);

    expect(event.defaultPrevented).toBe(true);
    expect(app.activeSection()).toBe('accounts');
    expect(globalThis.location.hash).toBe('#accounts');
  });

  it('restores the overview when navigation returns to the root URL', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    globalThis.history.replaceState(null, '', '#activity');
    app.syncSectionFromHash();
    expect(app.activeSection()).toBe('activity');

    globalThis.history.replaceState(null, '', '/');
    app.syncSectionFromHash();
    expect(app.activeSection()).toBe('overview');
  });
});
