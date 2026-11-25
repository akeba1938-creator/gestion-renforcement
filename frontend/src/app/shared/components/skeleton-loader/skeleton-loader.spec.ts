import { TestBed } from '@angular/core/testing';
import { SkeletonLoaderComponent } from './skeleton-loader';

describe('SkeletonLoaderComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SkeletonLoaderComponent],
    }).compileComponents();
  });

  it('renders kpi variant with accessible status attributes and requested count', () => {
    const fixture = TestBed.createComponent(SkeletonLoaderComponent);
    fixture.componentRef.setInput('variant', 'kpi');
    fixture.componentRef.setInput('count', 4);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const status = el.querySelector('[role="status"]');
    expect(status?.getAttribute('aria-busy')).toBe('true');
    expect(el.querySelectorAll('.grid > div').length).toBe(4);
  });

  it('renders table variant with requested rows and columns', () => {
    const fixture = TestBed.createComponent(SkeletonLoaderComponent);
    fixture.componentRef.setInput('variant', 'table');
    fixture.componentRef.setInput('count', 3);
    fixture.componentRef.setInput('columns', 5);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const bodyRows = el.querySelectorAll('.divide-y > div');
    expect(bodyRows.length).toBe(3);
    expect(bodyRows[0]?.querySelectorAll('.skeleton').length).toBe(5);
  });

  it('renders card variant cleanly', () => {
    const fixture = TestBed.createComponent(SkeletonLoaderComponent);
    fixture.componentRef.setInput('variant', 'card');
    fixture.componentRef.setInput('count', 2);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('.grid > div').length).toBe(2);
  });
});
