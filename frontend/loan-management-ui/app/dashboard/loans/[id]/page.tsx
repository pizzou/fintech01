'use client';
import { useState, useEffect, useCallback, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { createLoan, CreateLoanPayload } from '../../../../services/loanService';
import { getBorrowers } from '../../../../services/borrowerService';
import { get } from '../../../../services/api';
import { Borrower } from '../../../../types/index';
import { toast } from '../../../../hooks/useToast';
import { LOAN_TYPE_META } from '../../../../lib/utils';

const LOAN_TYPES = [
  'PERSONAL','BUSINESS','MORTGAGE','AUTO','STUDENT','EMERGENCY','ASSET_FINANCE',
  'SALARY_ADVANCE','MICROFINANCE','AGRICULTURAL','TRADE_FINANCE','GROUP',
];

interface LoanProductLite {
  loanType: string;
  interestRate: number;
  interestRateType: 'ANNUAL' | 'MONTHLY';
  active: boolean;
}

export default function NewLoanPage() {
  return (
    <Suspense fallback={<div className="p-6 text-sm text-gray-400">Loading…</div>}>
      <NewLoanForm />
    </Suspense>
  );
}

function NewLoanForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [borrowers,      setBorrowers]      = useState<Borrower[]>([]);
  const [products,       setProducts]       = useState<LoanProductLite[]>([]);
  const [loading,        setLoading]        = useState(false);
  const [borrowerId,     setBorrowerId]     = useState(searchParams.get('borrowerId') ?? '');
  const [loanType,       setLoanType]       = useState('PERSONAL');
  const [amount,         setAmount]         = useState('');
  const [interestRate,   setInterestRate]   = useState('');
  const [interestRateType, setInterestRateType] = useState<'ANNUAL' | 'MONTHLY'>('MONTHLY');
  const [rateTouched,    setRateTouched]    = useState(false); // true once the officer edits the rate by hand
  const [durationMonths, setDurationMonths] = useState('');
  const [currency,       setCurrency]       = useState('USD');
  const [startDate,      setStartDate]      = useState(new Date().toISOString().slice(0, 10));
  const [notes,          setNotes]          = useState('');
  const [collateralValue, setCollateralValue] = useState('');
  const [collateralDesc,  setCollateralDesc]  = useState('');

  useEffect(() => {
    getBorrowers()
      .then(data => setBorrowers(data as Borrower[]))
      .catch(console.error);
    get('/loan-products')
      .then((data: any) => setProducts((Array.isArray(data) ? data : []) as LoanProductLite[]))
      .catch(console.error);
  }, []);

  // Whenever the loan type changes (or products finish loading), pull that type's real
  // product config — e.g. Personal Loan is 10% per month, Business Loan is 12% p.a. —
  // instead of guessing. Only auto-fills the rate if the officer hasn't typed one in by hand.
  useEffect(() => {
    if (rateTouched) return;
    const product = products.find(p => p.loanType === loanType && p.active);
    if (product) {
      setInterestRate(String(product.interestRate));
      setInterestRateType(product.interestRateType);
    }
  }, [loanType, products, rateTouched]);

  const getMsg = (err: unknown) =>
    err instanceof Error ? err.message : 'Something went wrong';

  const monthlyPreview = (() => {
    const P = Number(amount);
    const r = interestRateType === 'MONTHLY'
      ? Number(interestRate) / 100
      : Number(interestRate) / 100 / 12; // ANNUAL rate converted to a monthly rate, same as the backend
    const n = Number(durationMonths);
    if (!P || !n) return null;
    if (r === 0) return (P / n).toFixed(2);
    const M = (P * r * Math.pow(1 + r, n)) / (Math.pow(1 + r, n) - 1);
    return isFinite(M) ? M.toFixed(2) : null;
  })();

  const ltv = (() => {
    const a = Number(amount);
    const c = Number(collateralValue);
    if (!a || !c) return null;
    return ((a / c) * 100).toFixed(1);
  })();

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    if (!borrowerId) { toast('error', 'Please select a borrower'); return; }
    setLoading(true);
    const payload: CreateLoanPayload = {
      borrowerId:            Number(borrowerId),
      loanType:              loanType as CreateLoanPayload['loanType'],
      amount:                Number(amount),
      interestRate:          Number(interestRate),
      interestRateType,
      durationMonths:        Number(durationMonths),
      currency,
      startDate,
      notes:                 notes.trim() || undefined,
      collateralValue:       collateralValue ? Number(collateralValue) : undefined,
      collateralDescription: collateralDesc.trim() || undefined,
    };
    try {
      await createLoan(payload);
      toast('success', 'Loan application submitted!');
      router.push('/dashboard/loans');
    } catch (err: unknown) {
      toast('error', getMsg(err));
    } finally {
      setLoading(false);
    }
  }, [borrowerId, loanType, amount, interestRate, interestRateType, durationMonths, currency,
      startDate, notes, collateralValue, collateralDesc, router]);

  return (
    <div className="max-w-2xl">
      <div className="mb-6">
        <Link href="/dashboard/loans" className="text-sm text-gray-500 hover:text-gray-700">
          ← Back
        </Link>
        <h1 className="text-xl font-bold text-gray-900 mt-2">New Loan Application</h1>
      </div>

      <form onSubmit={handleSubmit} className="bg-white rounded-xl border border-gray-200 p-6 space-y-5">

        {/* Loan Type */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1.5">Loan Type *</label>
          <select
            value={loanType}
            onChange={e => { setLoanType(e.target.value); setRateTouched(false); }}
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            {LOAN_TYPES.map(t => (
              <option key={t} value={t}>{LOAN_TYPE_META[t]?.icon} {LOAN_TYPE_META[t]?.label ?? t}</option>
            ))}
          </select>
          {products.find(p => p.loanType === loanType && p.active) && (
            <p className="text-xs text-gray-400 mt-1">
              Rate auto-filled from your {LOAN_TYPE_META[loanType]?.label ?? loanType} product — edit it below if this loan needs a different rate.
            </p>
          )}
        </div>

        {/* Borrower */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1.5">Borrower *</label>
          <select
            value={borrowerId}
            onChange={e => setBorrowerId(e.target.value)}
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="">Select a borrower...</option>
            {borrowers.map(b => (
              <option key={b.id} value={b.id}>{b.firstName} {b.lastName}</option>
            ))}
          </select>
        </div>

        {/* Amount + Currency */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Amount *</label>
            <div className="flex">
              <select
                value={currency}
                onChange={e => setCurrency(e.target.value)}
                className="px-3 py-2.5 border border-r-0 border-gray-300 rounded-l-lg text-sm bg-gray-50 focus:outline-none"
              >
                {['USD','RWF','EUR','KES','GBP','NGN'].map(c => (
                  <option key={c}>{c}</option>
                ))}
              </select>
              <input
                type="number" min="1" required
                value={amount}
                onChange={e => setAmount(e.target.value)}
                placeholder="0.00"
                className="flex-1 px-4 py-2.5 border border-gray-300 rounded-r-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          </div>
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label className="block text-sm font-medium text-gray-700">
                Interest Rate ({interestRateType === 'MONTHLY' ? '% per month' : '% p.a.'}) *
              </label>
              <div className="flex rounded-md border border-gray-300 overflow-hidden text-xs font-semibold">
                <button
                  type="button"
                  onClick={() => { setInterestRateType('ANNUAL'); setRateTouched(true); }}
                  className={`px-2.5 py-1 transition-colors ${
                    interestRateType === 'ANNUAL' ? 'bg-blue-600 text-white' : 'bg-white text-gray-600 hover:bg-gray-50'
                  }`}
                >
                  p.a.
                </button>
                <button
                  type="button"
                  onClick={() => { setInterestRateType('MONTHLY'); setRateTouched(true); }}
                  className={`px-2.5 py-1 border-l border-gray-300 transition-colors ${
                    interestRateType === 'MONTHLY' ? 'bg-blue-600 text-white' : 'bg-white text-gray-600 hover:bg-gray-50'
                  }`}
                >
                  per month
                </button>
              </div>
            </div>
            <input
              type="number" step="0.1" min="0" required
              value={interestRate}
              onChange={e => { setInterestRate(e.target.value); setRateTouched(true); }}
              placeholder="e.g. 10"
              className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <div className="flex gap-1.5 mt-1.5">
              {(interestRateType === 'MONTHLY' ? [6, 8, 10] : [10, 12, 15]).map(r => (
                <button key={r} type="button" onClick={() => { setInterestRate(String(r)); setRateTouched(true); }}
                  className={`text-xs px-2.5 py-1 rounded border font-semibold transition-colors ${
                    interestRate === String(r) ? 'bg-blue-50 text-blue-700 border-blue-300' : 'border-gray-300 text-gray-600 hover:border-blue-400'
                  }`}>
                  {r}{interestRateType === 'MONTHLY' ? '%/mo' : '% p.a.'}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Duration + Start Date */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              Duration (months) *
            </label>
            <input
              type="number" min="1" max="360" required
              value={durationMonths}
              onChange={e => setDurationMonths(e.target.value)}
              placeholder="e.g. 12"
              className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Start Date *</label>
            <input
              type="date" required
              value={startDate}
              onChange={e => setStartDate(e.target.value)}
              className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>

        {/* Collateral */}
        <div className="border-t border-gray-100 pt-5">
          <h3 className="text-sm font-semibold text-gray-700 mb-3">
            Collateral <span className="text-gray-400 font-normal">(optional)</span>
          </h3>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">
                Collateral Value ({currency})
              </label>
              <input
                type="number" min="0"
                value={collateralValue}
                onChange={e => setCollateralValue(e.target.value)}
                placeholder="e.g. 50000"
                className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Description</label>
              <input
                type="text"
                value={collateralDesc}
                onChange={e => setCollateralDesc(e.target.value)}
                placeholder="e.g. Land title, Vehicle"
                className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          </div>
          {ltv && (
            <div className={`mt-3 flex items-center gap-2 text-xs font-medium px-3 py-2 rounded-lg ${
              Number(ltv) <= 70 ? 'bg-green-50 text-green-700' :
              Number(ltv) <= 90 ? 'bg-yellow-50 text-yellow-700' :
              'bg-red-50 text-red-700'
            }`}>
              <span>📊 Loan-to-Value (LTV): {ltv}%</span>
              <span>&mdash;</span>
              <span>
                {Number(ltv) <= 70
                  ? 'Excellent coverage'
                  : Number(ltv) <= 90
                  ? 'Acceptable'
                  : 'High risk — collateral may be insufficient'}
              </span>
            </div>
          )}
        </div>

        {/* Notes */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1.5">
            Notes <span className="text-gray-400 font-normal">(optional)</span>
          </label>
          <textarea
            value={notes}
            onChange={e => setNotes(e.target.value)}
            rows={2}
            placeholder="Purpose of loan, additional terms, etc."
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
          />
        </div>

        {/* Monthly preview */}
        {monthlyPreview && (
          <div className="bg-blue-50 border border-blue-200 rounded-xl p-4">
            <p className="text-sm text-blue-700 font-medium">Estimated monthly installment</p>
            <p className="text-2xl font-bold text-blue-800 mt-0.5">
              {currency} {monthlyPreview}
            </p>
            <p className="text-xs text-blue-500 mt-1">
              Reducing balance (amortization) method
            </p>
          </div>
        )}

        <div className="flex gap-3 pt-2">
          <button
            type="submit"
            disabled={loading}
            className="bg-blue-600 hover:bg-blue-700 text-white px-6 py-2.5 rounded-lg text-sm font-medium disabled:opacity-60 transition"
          >
            {loading ? 'Submitting...' : 'Submit Application'}
          </button>
          <Link
            href="/dashboard/loans"
            className="px-6 py-2.5 rounded-lg text-sm font-medium text-gray-600 hover:bg-gray-100 transition"
          >
            Cancel
          </Link>
        </div>
      </form>
    </div>
  );
}