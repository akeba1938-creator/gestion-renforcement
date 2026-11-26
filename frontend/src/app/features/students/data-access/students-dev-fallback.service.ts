import { Injectable, inject } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ProblemDetailError } from '../../../core/models/problem-detail.model';
import {
  CreateStudentInput,
  DEFAULT_STUDENT_CLASSES,
  StudentRecord,
  StudentsListQuery,
  StudentsPageResult,
  buildStudentInitials,
} from '../models/students.model';
import { StudentsDemoFallback } from './students-demo-fallback.token';

const DEV_DEMO_TOKEN = 'demo-memory-token';

/**
 * Purely fictional dataset for local development mode only (`/dev-login` without backend).
 * Never bundled in production builds (excluded via `app.dev-providers.prod.ts` file replacement).
 * Uses non-assignable Senegalese demo phone numbers (+2210000000XX).
 */
const INITIAL_DEMO_STUDENTS: readonly StudentRecord[] = [
  {
    id: 'stu-demo-001',
    matricule: 'DEMO-2026-001',
    firstName: 'Aminata',
    lastName: 'Diallo-Démo',
    fullName: 'Aminata Diallo-Démo',
    initials: 'AD',
    gender: 'FEMALE',
    birthDate: '2009-03-14',
    admissionDate: '2026-10-01',
    className: 'Terminale S2',
    guardianName: 'Mamadou Diallo-Démo',
    guardianPhone: '+221000000001',
    email: 'aminata.demo@ecole-demo.invalid',
    address: 'Sacré-Cœur 3, Dakar',
    status: 'ACTIVE',
    statusReason: null,
    balanceFcfa: 0,
    financialStatus: 'UP_TO_DATE',
    createdAt: '2026-10-01T09:00:00Z',
  },
  {
    id: 'stu-demo-002',
    matricule: 'DEMO-2026-002',
    firstName: 'Cheikh Tidiane',
    lastName: 'Ndiaye-Démo',
    fullName: 'Cheikh Tidiane Ndiaye-Démo',
    initials: 'CN',
    gender: 'MALE',
    birthDate: '2011-07-22',
    admissionDate: '2026-10-02',
    className: '3ème A',
    guardianName: 'Ousmane Ndiaye-Démo',
    guardianPhone: '+221000000002',
    email: null,
    address: 'Mermoz, Dakar',
    status: 'ACTIVE',
    statusReason: null,
    balanceFcfa: 45000,
    financialStatus: 'PARTIAL',
    createdAt: '2026-10-02T10:30:00Z',
  },
  {
    id: 'stu-demo-003',
    matricule: 'DEMO-2026-003',
    firstName: 'Fatou Bintou',
    lastName: 'Sow-Démo',
    fullName: 'Fatou Bintou Sow-Démo',
    initials: 'FS',
    gender: 'FEMALE',
    birthDate: '2014-11-05',
    admissionDate: '2026-10-03',
    className: '6ème B',
    guardianName: 'Mariama Sow-Démo',
    guardianPhone: '+221000000003',
    email: null,
    address: 'Parcelles Assainies U12, Dakar',
    status: 'ACTIVE',
    statusReason: null,
    balanceFcfa: 0,
    financialStatus: 'UP_TO_DATE',
    createdAt: '2026-10-03T08:15:00Z',
  },
  {
    id: 'stu-demo-004',
    matricule: 'DEMO-2026-004',
    firstName: 'Mouhamadou',
    lastName: 'Fall-Démo',
    fullName: 'Mouhamadou Fall-Démo',
    initials: 'MF',
    gender: 'MALE',
    birthDate: '2010-01-19',
    admissionDate: '2026-10-04',
    className: '1ère L',
    guardianName: 'Ibrahima Fall-Démo',
    guardianPhone: '+221000000004',
    email: null,
    address: 'Liberté 6, Dakar',
    status: 'PENDING',
    statusReason: 'Dossier administratif en cours de validation',
    balanceFcfa: 75000,
    financialStatus: 'OVERDUE',
    createdAt: '2026-10-04T11:45:00Z',
  },
  {
    id: 'stu-demo-005',
    matricule: 'DEMO-2026-005',
    firstName: 'Aïssatou',
    lastName: 'Ba-Démo',
    fullName: 'Aïssatou Ba-Démo',
    initials: 'AB',
    gender: 'FEMALE',
    birthDate: '2015-05-28',
    admissionDate: '2026-10-05',
    className: 'CM2 (Primaire)',
    guardianName: 'Abdoulaye Ba-Démo',
    guardianPhone: '+221000000005',
    email: null,
    address: 'Point E, Dakar',
    status: 'ACTIVE',
    statusReason: null,
    balanceFcfa: 0,
    financialStatus: 'UP_TO_DATE',
    createdAt: '2026-10-05T14:20:00Z',
  },
  {
    id: 'stu-demo-006',
    matricule: 'DEMO-2026-006',
    firstName: 'Moussa',
    lastName: 'Diop-Démo',
    fullName: 'Moussa Diop-Démo',
    initials: 'MD',
    gender: 'MALE',
    birthDate: '2010-09-10',
    admissionDate: '2026-10-06',
    className: 'Seconde S',
    guardianName: 'Seydou Diop-Démo',
    guardianPhone: '+221000000006',
    email: null,
    address: 'Grand Yoff, Dakar',
    status: 'ACTIVE',
    statusReason: null,
    balanceFcfa: 35000,
    financialStatus: 'PARTIAL',
    createdAt: '2026-10-06T16:00:00Z',
  },
  {
    id: 'stu-demo-007',
    matricule: 'DEMO-2026-007',
    firstName: 'Khady',
    lastName: 'Sall-Démo',
    fullName: 'Khady Sall-Démo',
    initials: 'KS',
    gender: 'FEMALE',
    birthDate: '2008-12-03',
    admissionDate: '2026-10-07',
    className: 'Terminale S2',
    guardianName: 'Awa Sall-Démo',
    guardianPhone: '+221000000007',
    email: null,
    address: 'Almadies, Dakar',
    status: 'WITHDRAWN',
    statusReason: 'Déménagement familial hors région',
    balanceFcfa: 120000,
    financialStatus: 'OVERDUE',
    createdAt: '2026-10-07T09:40:00Z',
  },
  {
    id: 'stu-demo-008',
    matricule: 'DEMO-2026-008',
    firstName: 'Oumar',
    lastName: 'Gueye-Démo',
    fullName: 'Oumar Gueye-Démo',
    initials: 'OG',
    gender: 'MALE',
    birthDate: '2011-04-17',
    admissionDate: '2026-10-08',
    className: '3ème A',
    guardianName: 'Babacar Gueye-Démo',
    guardianPhone: '+221000000008',
    email: null,
    address: 'Fann Résidence, Dakar',
    status: 'ACTIVE',
    statusReason: null,
    balanceFcfa: 0,
    financialStatus: 'UP_TO_DATE',
    createdAt: '2026-10-08T13:10:00Z',
  },
  {
    id: 'stu-demo-009',
    matricule: 'DEMO-2026-009',
    firstName: 'Ndèye Astou',
    lastName: 'Faye-Démo',
    fullName: 'Ndèye Astou Faye-Démo',
    initials: 'NF',
    gender: 'FEMALE',
    birthDate: '2014-08-29',
    admissionDate: '2026-10-09',
    className: '6ème B',
    guardianName: 'Modou Faye-Démo',
    guardianPhone: '+221000000009',
    email: null,
    address: 'Ouakam, Dakar',
    status: 'PENDING',
    statusReason: 'Pièce justificative de transfert en attente',
    balanceFcfa: 50000,
    financialStatus: 'PARTIAL',
    createdAt: '2026-10-09T08:50:00Z',
  },
  {
    id: 'stu-demo-010',
    matricule: 'DEMO-2026-010',
    firstName: 'Serigne',
    lastName: 'Mbaye-Démo',
    fullName: 'Serigne Mbaye-Démo',
    initials: 'SM',
    gender: 'MALE',
    birthDate: '2010-02-11',
    admissionDate: '2026-10-10',
    className: 'Seconde S',
    guardianName: 'Alioune Mbaye-Démo',
    guardianPhone: '+221000000010',
    email: null,
    address: 'HLM 5, Dakar',
    status: 'ACTIVE',
    statusReason: null,
    balanceFcfa: 0,
    financialStatus: 'UP_TO_DATE',
    createdAt: '2026-10-10T10:15:00Z',
  },
  {
    id: 'stu-demo-011',
    matricule: 'DEMO-2026-011',
    firstName: 'Ramatoulaye',
    lastName: 'Cissé-Démo',
    fullName: 'Ramatoulaye Cissé-Démo',
    initials: 'RC',
    gender: 'FEMALE',
    birthDate: '2015-10-08',
    admissionDate: '2026-10-11',
    className: 'CM2 (Primaire)',
    guardianName: 'Binta Cissé-Démo',
    guardianPhone: '+221000000011',
    email: null,
    address: 'Sicap Baobab, Dakar',
    status: 'ACTIVE',
    statusReason: null,
    balanceFcfa: 25000,
    financialStatus: 'PARTIAL',
    createdAt: '2026-10-11T15:05:00Z',
  },
  {
    id: 'stu-demo-012',
    matricule: 'DEMO-2026-012',
    firstName: 'Pape Abdou',
    lastName: 'Seck-Démo',
    fullName: 'Pape Abdou Seck-Démo',
    initials: 'PS',
    gender: 'MALE',
    birthDate: '2009-06-21',
    admissionDate: '2026-10-12',
    className: '1ère L',
    guardianName: 'Lamine Seck-Démo',
    guardianPhone: '+221000000012',
    email: null,
    address: 'Hann Maristes, Dakar',
    status: 'WITHDRAWN',
    statusReason: 'Radiation sur demande écrite du tuteur',
    balanceFcfa: 0,
    financialStatus: 'UP_TO_DATE',
    createdAt: '2026-10-12T12:00:00Z',
  },
];

@Injectable({ providedIn: 'root' })
export class StudentsDevFallbackService implements StudentsDemoFallback {
  private readonly authService = inject(AuthService);
  private students: StudentRecord[] = [...INITIAL_DEMO_STUDENTS];
  private sequence = INITIAL_DEMO_STUDENTS.length;

  isDemoSessionActive(): boolean {
    return this.authService.token() === DEV_DEMO_TOKEN;
  }

  list(query: StudentsListQuery): Observable<StudentsPageResult> {
    const search = query.search?.trim().toLowerCase() ?? '';
    const className = query.className?.trim() ?? '';
    const status = query.status ?? 'ALL';

    let filtered = this.students.filter((student) => {
      if (className && student.className !== className) {
        return false;
      }
      if (status !== 'ALL' && student.status !== status) {
        return false;
      }
      if (!search) {
        return true;
      }
      const normalizedSearchDigits = search.replace(/\s+/g, '');
      return (
        student.firstName.toLowerCase().includes(search) ||
        student.lastName.toLowerCase().includes(search) ||
        student.fullName.toLowerCase().includes(search) ||
        student.matricule.toLowerCase().includes(search) ||
        student.guardianName.toLowerCase().includes(search) ||
        student.guardianPhone.toLowerCase().includes(normalizedSearchDigits) ||
        student.className.toLowerCase().includes(search)
      );
    });

    const sortBy = query.sortBy ?? 'lastName';
    const direction = query.direction ?? 'ASC';
    const multiplier = direction === 'DESC' ? -1 : 1;

    filtered = [...filtered].sort((a, b) => {
      switch (sortBy) {
        case 'firstName':
          return a.firstName.localeCompare(b.firstName, 'fr') * multiplier;
        case 'matricule':
          return a.matricule.localeCompare(b.matricule, 'fr') * multiplier;
        case 'status':
          return a.status.localeCompare(b.status, 'fr') * multiplier;
        case 'createdAt':
          return a.createdAt.localeCompare(b.createdAt) * multiplier;
        case 'admissionDate':
          return a.admissionDate.localeCompare(b.admissionDate) * multiplier;
        case 'birthDate':
          return a.birthDate.localeCompare(b.birthDate) * multiplier;
        default:
          return a.lastName.localeCompare(b.lastName, 'fr') * multiplier;
      }
    });

    const size = Math.max(1, Math.min(100, Math.trunc(query.size || 8)));
    const totalElements = filtered.length;
    const totalPages = totalElements === 0 ? 0 : Math.ceil(totalElements / size);
    const requestedPage = Math.max(0, Math.trunc(query.page || 0));
    const safePage = totalPages === 0 ? 0 : Math.min(requestedPage, totalPages - 1);
    const start = safePage * size;
    const content = filtered.slice(start, start + size);

    const availableClasses = Array.from(
      new Set([...DEFAULT_STUDENT_CLASSES, ...this.students.map((s) => s.className)])
    );

    return of({
      content,
      page: safePage,
      size,
      totalElements,
      totalPages,
      first: safePage === 0,
      last: totalPages === 0 || safePage >= totalPages - 1,
      availableClasses,
    });
  }

  getById(studentId: string): Observable<StudentRecord> {
    const found = this.students.find((student) => student.id === studentId);
    if (!found) {
      return throwError(
        () =>
          new ProblemDetailError({
            type: 'urn:ecole:problem:not-found',
            title: 'Élève introuvable',
            status: 404,
            detail: "Le dossier élève demandé n'existe pas ou n'est plus accessible.",
            requestId: 'req-demo-student-404',
          })
      );
    }
    return of(found);
  }

  create(input: CreateStudentInput): Observable<StudentRecord> {
    this.sequence += 1;
    const padded = String(this.sequence).padStart(3, '0');
    const firstName = input.firstName.trim();
    const lastName = input.lastName.trim();
    const nowIso = new Date().toISOString();

    const created: StudentRecord = {
      id: `stu-demo-${padded}`,
      matricule: `DEMO-2026-${padded}`,
      firstName,
      lastName,
      fullName: `${firstName} ${lastName}`.trim(),
      initials: buildStudentInitials(firstName, lastName),
      gender: input.gender ?? 'FEMALE',
      birthDate: input.birthDate?.trim() || '2012-09-15',
      admissionDate: nowIso.slice(0, 10),
      className: input.className.trim() || DEFAULT_STUDENT_CLASSES[0],
      guardianName: input.guardianName.trim(),
      guardianPhone: input.guardianPhone.trim(),
      email: null,
      address: null,
      status: 'ACTIVE',
      statusReason: null,
      balanceFcfa: 0,
      financialStatus: 'UP_TO_DATE',
      createdAt: nowIso,
    };

    this.students = [created, ...this.students];
    return of(created);
  }
}
