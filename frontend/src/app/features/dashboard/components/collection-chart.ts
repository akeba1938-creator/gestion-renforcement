import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { FcfaPipe } from '../../../shared/pipes/fcfa.pipe';
import { MonthlyCollectionPoint } from '../models/dashboard.model';

interface ChartBarGeometry {
  readonly point: MonthlyCollectionPoint;
  readonly slotX: number;
  readonly slotWidth: number;
  readonly groupX: number;
  readonly targetPath: string;
  readonly collectedPath: string;
  readonly labelX: number;
  readonly tooltipLeftPercent: number;
  readonly achievementPercent: number;
  readonly tooltip: string;
}

interface GridTick {
  readonly valueFcfa: number;
  readonly label: string;
  readonly y: number;
}

const CHART_WIDTH = 600;
const CHART_HEIGHT = 220;
const MARGIN_LEFT = 48;
const MARGIN_RIGHT = 16;
const MARGIN_TOP = 16;
const MARGIN_BOTTOM = 34;
const PLOT_HEIGHT = CHART_HEIGHT - MARGIN_TOP - MARGIN_BOTTOM;
const PLOT_WIDTH = CHART_WIDTH - MARGIN_LEFT - MARGIN_RIGHT;
const BASELINE_Y = MARGIN_TOP + PLOT_HEIGHT;
const BAR_WIDTH = 16;
const BAR_GAP = 6;
const BAR_TOP_RADIUS = 5;

const INTEGER_FORMATTER = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 });

function formatIntegerNbsp(value: number): string {
  return INTEGER_FORMATTER.format(Math.trunc(value)).replace(/\u202F/g, '\u00A0');
}

/**
 * Generates an SVG path for a vertical bar with rounded top corners and a flat bottom
 * anchored cleanly on the baseline axis.
 */
function roundedTopBarPath(
  x: number,
  y: number,
  width: number,
  height: number,
  radius: number
): string {
  const safeHeight = Math.max(2, height);
  const r = Math.min(radius, width / 2, safeHeight);
  const bottom = y + safeHeight;
  return [
    `M ${x} ${bottom}`,
    `L ${x} ${y + r}`,
    `Q ${x} ${y} ${x + r} ${y}`,
    `L ${x + width - r} ${y}`,
    `Q ${x + width} ${y} ${x + width} ${y + r}`,
    `L ${x + width} ${bottom}`,
    'Z',
  ].join(' ');
}

/**
 * Lightweight, zero-dependency accessible SVG grouped bar chart comparing
 * monthly collected FCFA vs institutional targets over 6 months.
 */
@Component({
  selector: 'app-collection-chart',
  standalone: true,
  imports: [FcfaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section
      class="flex h-full flex-col rounded-xl border border-base-300/80 bg-base-100 p-4 shadow-xs sm:p-6"
      aria-labelledby="collection-chart-title"
    >
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div class="min-w-0">
          <h2
            id="collection-chart-title"
            class="font-display text-base font-semibold tracking-tight text-base-content"
          >
            Évolution des encaissements (FCFA)
          </h2>
          <p class="mt-0.5 text-xs text-base-content/60">
            Suivi semestriel comparé aux objectifs institutionnels
          </p>
        </div>

        <div class="flex flex-wrap items-center gap-3 text-xs">
          <span class="inline-flex items-center gap-1.5 text-base-content/75">
            <span class="h-2.5 w-2.5 rounded-full bg-primary" aria-hidden="true"></span>
            Réalisé
          </span>
          <span class="inline-flex items-center gap-1.5 text-base-content/60">
            <span class="h-2.5 w-2.5 rounded-full bg-primary/25" aria-hidden="true"></span>
            Objectif
          </span>
          <span
            class="rounded-lg border border-base-300/80 bg-base-200/60 px-2.5 py-1 font-medium whitespace-nowrap text-base-content/75"
          >
            Derniers 6 mois
          </span>
        </div>
      </div>

      <div class="relative mt-5 flex-1" (mouseleave)="clearActiveMonth()">
        @if (activeBar(); as active) {
          <div
            role="tooltip"
            data-testid="collection-chart-tooltip"
            class="pointer-events-none absolute top-0 z-10 w-56 -translate-x-1/2 rounded-xl border border-base-300/80 bg-base-100/95 px-3 py-2.5 text-xs shadow-sm backdrop-blur-xs transition-opacity duration-150 ease-out motion-reduce:transition-none"
            [style.left.%]="active.tooltipLeftPercent"
          >
            <div class="flex items-center justify-between gap-2 border-b border-base-300/70 pb-1.5">
              <span class="font-display font-semibold text-base-content">
                {{ active.point.monthLabel }}
              </span>
              <span
                class="inline-flex items-center rounded-md border px-1.5 py-0.5 text-[11px] font-medium whitespace-nowrap tabular-nums"
                [class]="
                  active.achievementPercent >= 100
                    ? 'border-success/20 bg-success/10 text-success'
                    : 'border-warning/20 bg-warning/10 text-warning'
                "
              >
                {{ active.achievementPercent }} %
              </span>
            </div>

            <dl class="mt-2 space-y-1">
              <div class="flex items-center justify-between gap-3">
                <dt class="inline-flex items-center gap-1.5 text-base-content/65">
                  <span class="h-2 w-2 rounded-full bg-primary" aria-hidden="true"></span>
                  Réalisé
                </dt>
                <dd class="font-semibold whitespace-nowrap tabular-nums text-base-content">
                  {{ active.point.collectedFcfa | fcfa }}
                </dd>
              </div>
              <div class="flex items-center justify-between gap-3">
                <dt class="inline-flex items-center gap-1.5 text-base-content/65">
                  <span class="h-2 w-2 rounded-full bg-primary/30" aria-hidden="true"></span>
                  Objectif
                </dt>
                <dd class="font-medium whitespace-nowrap tabular-nums text-base-content/75">
                  {{ active.point.targetFcfa | fcfa }}
                </dd>
              </div>
            </dl>
          </div>
        }

        <svg
          [attr.viewBox]="viewBox"
          class="h-36 w-full overflow-visible sm:h-52 lg:h-56"
          role="img"
          [attr.aria-label]="ariaSummary()"
        >
          <title>Évolution des encaissements mensuels en FCFA sur les 6 derniers mois</title>

          <!-- Horizontal gridlines -->
          @for (tick of gridTicks(); track tick.valueFcfa) {
            <g>
              <line
                [attr.x1]="marginLeft"
                [attr.x2]="chartWidth - marginRight"
                [attr.y1]="tick.y"
                [attr.y2]="tick.y"
                class="stroke-base-300/60"
                stroke-width="1"
                stroke-dasharray="3 3"
              />
              <text
                [attr.x]="marginLeft - 10"
                [attr.y]="tick.y + 4"
                text-anchor="end"
                class="fill-base-content/55 tabular-nums"
                font-size="11"
              >
                {{ tick.label }}
              </text>
            </g>
          }

          <!-- Clean solid baseline -->
          <line
            [attr.x1]="marginLeft"
            [attr.x2]="chartWidth - marginRight"
            [attr.y1]="baselineY"
            [attr.y2]="baselineY"
            class="stroke-base-300/80"
            stroke-width="1"
          />

          @for (bar of bars(); track bar.point.monthKey) {
            <g
              tabindex="0"
              role="button"
              [attr.aria-label]="bar.tooltip"
              [attr.data-month-key]="bar.point.monthKey"
              class="cursor-pointer outline-none focus-visible:outline-none"
              (mouseenter)="setActiveMonth(bar.point.monthKey)"
              (mouseleave)="clearActiveMonth(bar.point.monthKey)"
              (focus)="setActiveMonth(bar.point.monthKey)"
              (blur)="clearActiveMonth(bar.point.monthKey)"
              (keydown.escape)="clearActiveMonth()"
            >
              <title>{{ bar.tooltip }}</title>

              <!-- Interactive hit area & soft hover/focus backdrop -->
              <rect
                [attr.x]="bar.slotX + 4"
                [attr.y]="marginTop - 4"
                [attr.width]="bar.slotWidth - 8"
                [attr.height]="plotHeight + 4"
                rx="8"
                class="fill-base-200/75 transition-opacity duration-150 ease-out motion-reduce:transition-none"
                [attr.opacity]="isBarActive(bar.point.monthKey) ? '1' : '0'"
              />

              <!-- Objectif bar (top-rounded only) -->
              <path
                [attr.d]="bar.targetPath"
                class="fill-primary/25 transition-opacity duration-200 ease-out motion-reduce:transition-none"
                [attr.opacity]="barOpacity(bar.point.monthKey)"
              />

              <!-- Réalisé bar (top-rounded only) -->
              <path
                [attr.d]="bar.collectedPath"
                class="fill-primary transition-opacity duration-200 ease-out motion-reduce:transition-none"
                [attr.opacity]="barOpacity(bar.point.monthKey)"
              />

              <text
                [attr.x]="bar.labelX"
                [attr.y]="chartHeight - 10"
                text-anchor="middle"
                [class]="
                  isBarActive(bar.point.monthKey) || bar.point.isPeak
                    ? 'fill-base-content font-semibold'
                    : 'fill-base-content/60 font-medium'
                "
                font-size="11"
              >
                {{ bar.point.monthLabel }}
              </text>
            </g>
          }
        </svg>

        <!-- Accessible data table equivalent for screen readers -->
        <table class="sr-only">
          <caption>Évolution des encaissements en FCFA sur 6 mois</caption>
          <thead>
            <tr>
              <th scope="col">Mois</th>
              <th scope="col">Objectif (FCFA)</th>
              <th scope="col">Réalisé (FCFA)</th>
            </tr>
          </thead>
          <tbody>
            @for (point of points(); track point.monthKey) {
              <tr>
                <th scope="row">{{ point.monthLabel }}</th>
                <td>{{ point.targetFcfa | fcfa }}</td>
                <td>{{ point.collectedFcfa | fcfa }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      <div
        class="mt-4 flex flex-col gap-2 rounded-xl border border-base-300/80 bg-base-200/50 px-4 py-3 text-xs sm:flex-row sm:items-center sm:justify-between"
      >
        <p class="text-base-content/65">{{ note() }}</p>
        <p class="font-display text-sm font-bold tracking-tight whitespace-nowrap tabular-nums text-base-content">
          Total cumulé : {{ totalCumulativeFcfa() | fcfa }}
        </p>
      </div>
    </section>
  `,
})
export class CollectionChartComponent {
  readonly points = input.required<readonly MonthlyCollectionPoint[]>();
  readonly totalCumulativeFcfa = input.required<number>();
  readonly note = input<string>('');

  protected readonly viewBox = `0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`;
  protected readonly chartWidth = CHART_WIDTH;
  protected readonly chartHeight = CHART_HEIGHT;
  protected readonly marginLeft = MARGIN_LEFT;
  protected readonly marginRight = MARGIN_RIGHT;
  protected readonly marginTop = MARGIN_TOP;
  protected readonly plotHeight = PLOT_HEIGHT;
  protected readonly baselineY = BASELINE_Y;

  protected readonly activeMonthKey = signal<string | null>(null);

  protected readonly maxScaleFcfa = computed(() => {
    const highest = this.points().reduce(
      (max, p) => Math.max(max, p.collectedFcfa, p.targetFcfa),
      0
    );
    if (highest <= 0) {
      return 8000000;
    }
    const step = 2000000;
    return Math.ceil(highest / step) * step;
  });

  protected readonly gridTicks = computed<readonly GridTick[]>(() => {
    const max = this.maxScaleFcfa();
    const steps = 4;
    const ticks: GridTick[] = [];
    for (let i = 1; i <= steps; i += 1) {
      const valueFcfa = Math.round((max * i) / steps);
      const ratio = valueFcfa / max;
      const y = MARGIN_TOP + PLOT_HEIGHT * (1 - ratio);
      const label = `${(valueFcfa / 1000000).toFixed(1)}M`;
      ticks.push({ valueFcfa, label, y });
    }
    return ticks;
  });

  protected readonly bars = computed<readonly ChartBarGeometry[]>(() => {
    const items = this.points();
    if (items.length === 0) {
      return [];
    }
    const max = this.maxScaleFcfa();
    const slotWidth = PLOT_WIDTH / items.length;
    const pairWidth = BAR_WIDTH * 2 + BAR_GAP;

    return items.map((point, index) => {
      const slotX = MARGIN_LEFT + slotWidth * index;
      const slotCenter = slotX + slotWidth / 2;
      const groupX = slotCenter - pairWidth / 2;
      const targetHeight = Math.max(4, (point.targetFcfa / max) * PLOT_HEIGHT);
      const collectedHeight = Math.max(4, (point.collectedFcfa / max) * PLOT_HEIGHT);
      const targetY = BASELINE_Y - targetHeight;
      const collectedY = BASELINE_Y - collectedHeight;

      const formattedCollected = formatIntegerNbsp(point.collectedFcfa);
      const formattedTarget = formatIntegerNbsp(point.targetFcfa);
      const achievementPercent =
        point.targetFcfa > 0 ? Math.round((point.collectedFcfa / point.targetFcfa) * 100) : 0;
      const rawPercent = (slotCenter / CHART_WIDTH) * 100;
      const tooltipLeftPercent = Math.min(80, Math.max(20, rawPercent));

      return {
        point,
        slotX,
        slotWidth,
        groupX,
        targetPath: roundedTopBarPath(groupX, targetY, BAR_WIDTH, targetHeight, BAR_TOP_RADIUS),
        collectedPath: roundedTopBarPath(
          groupX + BAR_WIDTH + BAR_GAP,
          collectedY,
          BAR_WIDTH,
          collectedHeight,
          BAR_TOP_RADIUS
        ),
        labelX: slotCenter,
        tooltipLeftPercent,
        achievementPercent,
        tooltip: `${point.monthLabel} — Réalisé : ${formattedCollected} FCFA / Objectif : ${formattedTarget} FCFA`,
      };
    });
  });

  protected readonly activeBar = computed<ChartBarGeometry | null>(() => {
    const key = this.activeMonthKey();
    if (!key) {
      return null;
    }
    return this.bars().find((bar) => bar.point.monthKey === key) ?? null;
  });

  protected readonly ariaSummary = computed(() => {
    const count = this.points().length;
    const total = formatIntegerNbsp(this.totalCumulativeFcfa());
    return `Graphique d'évolution des encaissements sur ${count} mois. Total cumulé : ${total} FCFA.`;
  });

  protected setActiveMonth(monthKey: string): void {
    this.activeMonthKey.set(monthKey);
  }

  protected clearActiveMonth(monthKey?: string): void {
    if (!monthKey || this.activeMonthKey() === monthKey) {
      this.activeMonthKey.set(null);
    }
  }

  protected isBarActive(monthKey: string): boolean {
    return this.activeMonthKey() === monthKey;
  }

  protected barOpacity(monthKey: string): string {
    const active = this.activeMonthKey();
    if (!active || active === monthKey) {
      return '1';
    }
    return '0.45';
  }
}
