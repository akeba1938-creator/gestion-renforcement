import { HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { ProblemDetailError, parseProblemDetail } from '../models/problem-detail.model';

/**
 * Global functional HTTP interceptor normalizing all HTTP errors into a typed
 * RFC 9457 ProblemDetailError suitable for UI display without leaking technical details.
 */
export const problemDetailInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((error: unknown) => {
      const problem = parseProblemDetail(error);
      return throwError(() => new ProblemDetailError(problem));
    })
  );
