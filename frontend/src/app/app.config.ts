import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { FINANCE_API } from './core/api/finance.api';
import { FinanceMockApi } from './core/api/finance-mock.api';
import { STUDENT_API } from './core/api/student.api';
import { StudentMockApi } from './core/api/student-mock.api';
import { authInterceptor } from './core/interceptors/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    { provide: STUDENT_API, useExisting: StudentMockApi },
    { provide: FINANCE_API, useExisting: FinanceMockApi },
  ],
};
