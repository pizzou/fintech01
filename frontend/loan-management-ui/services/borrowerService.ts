import { get, post, put } from './api';
import { Borrower } from '../types/index';

export const getBorrowers    = (search?: string): Promise<Borrower[]> =>
  get(`/borrowers${search ? `?search=${encodeURIComponent(search)}` : ''}`) as Promise<Borrower[]>;
export const getBorrowerById = (id: number): Promise<Borrower> =>
  get(`/borrowers/${id}`) as Promise<Borrower>;
export const createBorrower  = (p: Partial<Borrower>): Promise<Borrower> =>
  post('/borrowers', p) as Promise<Borrower>;
export const updateBorrower  = (id: number, p: Partial<Borrower>): Promise<Borrower> =>
  put(`/borrowers/${id}`, p) as Promise<Borrower>;