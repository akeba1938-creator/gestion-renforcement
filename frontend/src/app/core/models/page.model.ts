export type SortDirection = 'asc' | 'desc';

export interface PageQuery {
  page: number;
  size: number;
  search?: string;
  sortBy?: string;
  sortDir?: SortDirection;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}
