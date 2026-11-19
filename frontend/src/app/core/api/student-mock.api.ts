import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page, PageQuery } from '../models/page.model';
import { Student } from '../models/student.model';
import {
  MOCK_API_OPTIONS,
  MockApiOptions,
  MockScenario,
  buildMockPage,
  simulateMockRequest,
} from './mock-api.util';
import { StudentApi } from './student.api';

/**
 * Purely fictional student dataset for frontend development and tests.
 * Uses non-assignable phone numbers (+221 00 000 00 0X) and synthetic names.
 * Never use real student personal data in development.
 */
const FICTIONAL_STUDENTS: readonly Student[] = [
  {
    id: 'stu-demo-001',
    matricule: 'DEMO-2026-001',
    firstName: 'Awa',
    lastName: 'Ndiaye-Démo',
    className: 'Terminale S2',
    guardianPhone: '+221000000001',
    status: 'ACTIVE',
    enrolledAtUtc: '2026-10-01T09:00:00Z',
  },
  {
    id: 'stu-demo-002',
    matricule: 'DEMO-2026-002',
    firstName: 'Moussa',
    lastName: 'Diop-Démo',
    className: '3ème A',
    guardianPhone: '+221000000002',
    status: 'ACTIVE',
    enrolledAtUtc: '2026-10-02T10:30:00Z',
  },
  {
    id: 'stu-demo-003',
    matricule: 'DEMO-2026-003',
    firstName: 'Fatou',
    lastName: 'Sall-Démo',
    className: 'CM2 B',
    guardianPhone: '+221000000003',
    status: 'ACTIVE',
    enrolledAtUtc: '2026-10-03T08:15:00Z',
  },
  {
    id: 'stu-demo-004',
    matricule: 'DEMO-2026-004',
    firstName: 'Ibrahima',
    lastName: 'Fall-Démo',
    className: 'Seconde L',
    guardianPhone: '+221000000004',
    status: 'ACTIVE',
    enrolledAtUtc: '2026-10-04T11:45:00Z',
  },
  {
    id: 'stu-demo-005',
    matricule: 'DEMO-2026-005',
    firstName: 'Khady',
    lastName: 'Ba-Démo',
    className: '6ème A',
    guardianPhone: '+221000000005',
    status: 'ACTIVE',
    enrolledAtUtc: '2026-10-05T14:20:00Z',
  },
  {
    id: 'stu-demo-006',
    matricule: 'DEMO-2026-006',
    firstName: 'Cheikh',
    lastName: 'Sow-Démo',
    className: 'Première S1',
    guardianPhone: '+221000000006',
    status: 'INACTIVE',
    enrolledAtUtc: '2026-10-06T16:00:00Z',
  },
];

const STUDENT_COMPARATORS: Record<string, (a: Student, b: Student) => number> = {
  lastName: (a, b) => a.lastName.localeCompare(b.lastName, 'fr'),
  firstName: (a, b) => a.firstName.localeCompare(b.firstName, 'fr'),
  className: (a, b) => a.className.localeCompare(b.className, 'fr'),
  status: (a, b) => a.status.localeCompare(b.status),
  enrolledAtUtc: (a, b) => a.enrolledAtUtc.localeCompare(b.enrolledAtUtc),
};

@Injectable({ providedIn: 'root' })
export class StudentMockApi implements StudentApi {
  private readonly defaultOptions = inject(MOCK_API_OPTIONS);
  private options: MockApiOptions = { ...this.defaultOptions };

  configureMock(partial: Partial<MockApiOptions>): void {
    this.options = { ...this.options, ...partial };
  }

  setScenario(scenario: MockScenario): void {
    this.options = { ...this.options, scenario };
  }

  list(query: PageQuery): Observable<Page<Student>> {
    const emptyPage: Page<Student> = {
      items: [],
      page: Math.max(0, Math.trunc(query.page)),
      size: Math.max(1, Math.min(100, Math.trunc(query.size))),
      total: 0,
    };

    return simulateMockRequest(
      this.options,
      'Impossible de charger la liste des élèves. Veuillez réessayer.',
      emptyPage,
      () =>
        buildMockPage(
          FICTIONAL_STUDENTS,
          query,
          (student, term) =>
            student.firstName.toLowerCase().includes(term) ||
            student.lastName.toLowerCase().includes(term) ||
            student.matricule.toLowerCase().includes(term) ||
            student.className.toLowerCase().includes(term),
          STUDENT_COMPARATORS
        )
    );
  }

  getById(id: string): Observable<Student | null> {
    return simulateMockRequest(
      this.options,
      'Impossible de charger la fiche élève. Veuillez réessayer.',
      null,
      () => FICTIONAL_STUDENTS.find((student) => student.id === id) ?? null
    );
  }
}
