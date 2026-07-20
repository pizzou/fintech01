'use client';
import { useState } from 'react';
import { useTenant } from '../layout';
import { publicApi } from '@/services/api';
import DocumentUploadPanel from '@/components/DocumentUploadPanel';

interface StatusStep { label: string; complete: boolean; failed: boolean; }
interface Comment { message: string; createdAt: string; from: string; }
interface TrackResult {
  reference: string; status: string; statusLabel: string; statusSteps: StatusStep[];
  loanType: string; amount: number; currency: string;
  submittedDate: string; updatedDate: string; rejectionReason?: string; maritalStatus?: string;
}

export default function TrackPage() {
  const tenant = useTenant();
  const [reference, setReference] = useState('');
  const [phone, setPhone]         = useState('');
  const [loading, setLoading]     = useState(false);
  const [error, setError]         = useState('');
  const [result, setResult]       = useState<TrackResult | null>(null);
  const [comments, setComments]   = useState<Comment[]>([]);
  const [commentsError, setCommentsError] = useState(false);

  const primary = tenant?.primaryColor ?? '#0D6B3E';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(''); setResult(null); setComments([]); setCommentsError(false); setLoading(true);
    try {
      const data = await publicApi.trackApplication(reference, phone);
      setResult(data as TrackResult);
      publicApi.trackComments(reference, phone)
        .then((c) => setComments(c as Comment[]))
        .catch(() => setCommentsError(true));
    } catch (err: any) {
      setError(err.message || 'We could not find that application.');
    } finally {
      setLoading(false);
    }
  };

  const fmt = (n: number) => (n ?? 0).toLocaleString();
  const fmtDate = (d?: string) => d ? new Date(d).toLocaleDateString('en-RW', { year: 'numeric', month: 'short', day: 'numeric' }) : '—';
  const fmtDateTime = (d?: string) => d ? new Date(d).toLocaleString('en-RW', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—';

  return (
    <div>
      <section className="py-16 text-white text-center"
        style={{ background: `linear-gradient(135deg, ${primary} 0%, #0a4a2b 100%)` }}>
        <div className="max-w-2xl mx-auto px-4">
          <h1 className="text-3xl md:text-4xl font-bold mb-3 tracking-tight">Track Your Application</h1>
          <p className="text-white/75">Enter your reference number and phone number to check your loan application status.</p>
        </div>
      </section>

      <section className="max-w-lg mx-auto px-4 -mt-10 pb-24">
        <div className="bg-white rounded-xl shadow-xl border border-gray-100 p-6 md:p-8">
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-1.5">Reference Number</label>
              <input
                required value={reference}
                onChange={e => setReference(e.target.value)}
                placeholder="e.g. GFS-2026-000123"
                className="w-full px-3 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-offset-0"
                style={{ borderColor: '#d1d5db' }}
              />
            </div>
            <div>
              <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-1.5">Phone Number</label>
              <input
                required value={phone}
                onChange={e => setPhone(e.target.value)}
                placeholder="Phone number used on your application"
                className="w-full px-3 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-offset-0"
                style={{ borderColor: '#d1d5db' }}
              />
            </div>
            {error && (
              <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-lg px-3 py-2.5">{error}</div>
            )}
            <button type="submit" disabled={loading}
              className="w-full py-3 rounded-md font-bold text-white shadow-sm hover:opacity-90 transition-opacity disabled:opacity-60"
              style={{ backgroundColor: primary }}>
              {loading ? 'Checking…' : 'Check Status'}
            </button>
          </form>
        </div>

        {result && (
          <div className="mt-6 bg-white rounded-xl shadow-xl border border-gray-100 p-6 md:p-8">
            <div className="flex items-start justify-between mb-6">
              <div>
                <div className="text-xs font-bold text-gray-400 uppercase tracking-wider">Reference</div>
                <div className="font-mono font-bold text-gray-900">{result.reference}</div>
              </div>
              <span className="px-3 py-1.5 rounded-full text-xs font-bold"
                style={{ backgroundColor: primary + '15', color: primary }}>
                {result.statusLabel}
              </span>
            </div>

            {/* Progress steps */}
            <div className="flex items-center mb-8">
              {result.statusSteps.map((step, i) => (
                <div key={step.label} className="flex-1 flex items-center">
                  <div className="flex flex-col items-center flex-1">
                    <div className="w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold text-white"
                      style={{ backgroundColor: step.failed ? '#dc2626' : step.complete ? primary : '#e5e7eb', color: step.complete || step.failed ? '#fff' : '#9ca3af' }}>
                      {step.failed ? '✕' : step.complete ? '✓' : i + 1}
                    </div>
                    <div className="text-[10px] text-center text-gray-500 mt-1.5 leading-tight px-1">{step.label}</div>
                  </div>
                  {i < result.statusSteps.length - 1 && (
                    <div className="h-0.5 flex-1 -mt-4" style={{ backgroundColor: step.complete ? primary : '#e5e7eb' }} />
                  )}
                </div>
              ))}
            </div>

            {result.rejectionReason && (
              <div className="bg-red-50 border border-red-100 text-red-700 text-sm rounded-lg px-3 py-2.5 mb-4">
                {result.rejectionReason}
              </div>
            )}

            <div className="grid grid-cols-2 gap-4 text-sm border-t border-gray-100 pt-4">
              <div><span className="text-gray-400">Amount</span><div className="font-semibold text-gray-900">{result.currency} {fmt(result.amount)}</div></div>
              <div><span className="text-gray-400">Loan Type</span><div className="font-semibold text-gray-900">{result.loanType}</div></div>
              <div><span className="text-gray-400">Submitted</span><div className="font-semibold text-gray-900">{fmtDate(result.submittedDate)}</div></div>
              <div><span className="text-gray-400">Last Updated</span><div className="font-semibold text-gray-900">{fmtDate(result.updatedDate)}</div></div>
            </div>
          </div>
        )}

        {result && (
          <div className="mt-6 bg-white rounded-xl shadow-xl border border-gray-100 p-6 md:p-8">
            <h3 className="font-bold text-gray-900 mb-1">Updates from {tenant?.name ?? 'our team'}</h3>
            <p className="text-xs text-gray-400 mb-4">Messages from your loan officer, including any additional documents requested.</p>
            {commentsError && (
              <p className="text-sm text-amber-600 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2">
                We couldn't load updates right now. Please refresh the page or try again shortly.
              </p>
            )}
            {!commentsError && comments.length === 0 && (
              <p className="text-sm text-gray-400">No updates yet — check back after your application has been reviewed.</p>
            )}
            {!commentsError && comments.length > 0 && (
              <div className="space-y-3">
                {comments.map((c, i) => (
                  <div key={i} className="border-l-2 pl-4 py-1" style={{ borderColor: primary }}>
                    <p className="text-sm text-gray-800">{c.message}</p>
                    <p className="text-xs text-gray-400 mt-1">{c.from} · {fmtDateTime(c.createdAt)}</p>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {result && (
          <div className="mt-6">
            <DocumentUploadPanel
              reference={result.reference}
              phone={phone}
              maritalStatus={result.maritalStatus}
              primary={primary}
            />
          </div>
        )}
      </section>
    </div>
  );
}
