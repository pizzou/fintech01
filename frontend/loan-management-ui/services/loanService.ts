import { get, post } from './api';
import { Loan, RiskScore } from '../types/index';

export interface CreateLoanPayload {
  borrowerId:             number;
  amount:                 number;
  interestRate:           number;
  /** 'MONTHLY' (e.g. 10% per month — common for microfinance/salary-advance products) or
   *  'ANNUAL'. Defaults to ANNUAL on the backend if omitted, so this must be sent explicitly
   *  whenever the rate entered is a monthly one. */
  interestRateType?:      'MONTHLY' | 'ANNUAL';
  durationMonths:         number;
  currency:               string;
  startDate:              string;
  notes?:                 string;
  collateralValue?:       number;
  collateralDescription?: string;
}

export const getLoans           = (): Promise<Loan[]>         =>
  get('/loans?page=0&size=5000').then((r: any) => (Array.isArray(r) ? r : (r?.content ?? [])));
export const getLoanById        = (id: number): Promise<Loan> => get(`/loans/${id}`) as Promise<Loan>;
export const getLoansByBorrower = (id: number): Promise<Loan[]> => get(`/loans/borrower/${id}`) as Promise<Loan[]>;
export const createLoan         = (p: CreateLoanPayload): Promise<Loan> => post('/loans', p) as Promise<Loan>;
export const approveLoan        = (id: number): Promise<Loan> => post(`/loans/${id}/approve`) as Promise<Loan>;
export const rejectLoan         = (id: number, reason: string): Promise<Loan> =>
  post(`/loans/${id}/reject`, { reason }) as Promise<Loan>;
export const getLoanRiskScore   = (id: number): Promise<RiskScore> =>
  get(`/loans/${id}/risk`) as Promise<RiskScore>;