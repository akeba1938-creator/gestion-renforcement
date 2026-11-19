import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { Page, PageQuery } from '../models/page.model';
import { Student } from '../models/student.model';

export interface StudentApi {
  list(query: PageQuery): Observable<Page<Student>>;
  getById(id: string): Observable<Student | null>;
}

export const STUDENT_API = new InjectionToken<StudentApi>('STUDENT_API');
