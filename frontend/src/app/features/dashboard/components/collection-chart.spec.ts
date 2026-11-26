import { ChangeDetectionStrategy, reflectComponentType } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MonthlyCollectionPoint } from '../models/dashboard.model';
import { CollectionChartComponent } from './collection-chart';

const SAMPLE_POINTS: readonly MonthlyCollectionPoint[] = [
  { monthKey: '2026-09', monthLabel: 'Sept*', targetFcfa: 7050000, collectedFcfa: 7800000, isPeak: true },
  { monthKey: '2026-10', monthLabel: 'Oct', targetFcfa: 5900000, collectedFcfa: 6050000 },
  { monthKey: '2026-11', monthLabel: 'Nov', targetFcfa: 6150000, collectedFcfa: 6300000 },
];

describe('CollectionChartComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CollectionChartComponent],
    }).compileComponents();
  });

  it('uses ChangeDetectionStrategy.OnPush and is standalone', () => {
    const mirror = reflectComponentType(CollectionChartComponent);
    expect(mirror?.isStandalone).toBe(true);
    const cmpDef = (CollectionChartComponent as unknown as { ɵcmp?: { onPush?: boolean } }).ɵcmp;
    expect(cmpDef?.onPush).toBe(true);
    expect(ChangeDetectionStrategy.OnPush).toBeDefined();
  });

  it('renders subtle horizontal gridlines, top-rounded SVG bar paths, and interactive hover/focus tooltips', () => {
    const fixture = TestBed.createComponent(CollectionChartComponent);
    fixture.componentRef.setInput('points', SAMPLE_POINTS);
    fixture.componentRef.setInput('totalCumulativeFcfa', 20150000);
    fixture.componentRef.setInput('note', 'Pic de rentrée en septembre');
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;

    // Subtle horizontal gridlines with stroke-base-300/60
    const gridLines = el.querySelectorAll('svg line.stroke-base-300\\/60');
    expect(gridLines.length).toBe(4);

    // Top-rounded bar paths (2 bars per month)
    const barPaths = el.querySelectorAll('svg g[data-month-key] path');
    expect(barPaths.length).toBe(SAMPLE_POINTS.length * 2);

    // Tooltip hidden initially
    expect(el.querySelector('[data-testid="collection-chart-tooltip"]')).toBeNull();

    // Hovering a month group displays the interactive tooltip with Réalisé vs Objectif in FCFA
    const septGroup = el.querySelector<SVGGElement>('g[data-month-key="2026-09"]');
    expect(septGroup).not.toBeNull();
    septGroup?.dispatchEvent(new Event('mouseenter'));
    fixture.detectChanges();

    const tooltip = el.querySelector('[data-testid="collection-chart-tooltip"]');
    expect(tooltip).not.toBeNull();
    expect(tooltip?.textContent).toContain('Sept*');
    expect(tooltip?.textContent).toContain('7\u00A0800\u00A0000 FCFA');
    expect(tooltip?.textContent).toContain('7\u00A0050\u00A0000 FCFA');

    // Leaving clears tooltip
    septGroup?.dispatchEvent(new Event('mouseleave'));
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="collection-chart-tooltip"]')).toBeNull();

    // Keyboard focus also activates the tooltip and blur clears it
    const octGroup = el.querySelector<SVGGElement>('g[data-month-key="2026-10"]');
    octGroup?.dispatchEvent(new Event('focus'));
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="collection-chart-tooltip"]')?.textContent).toContain(
      '6\u00A0050\u00A0000 FCFA'
    );

    octGroup?.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="collection-chart-tooltip"]')).toBeNull();
  });
});
