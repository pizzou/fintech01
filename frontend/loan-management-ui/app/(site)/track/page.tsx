'use client';
import { useState } from 'react';
import { useTenant } from '../layout';
import { publicApi } from '@/services/api';
import DocumentUploadPanel from '@/components/DocumentUploadPanel';
import axios from 'axios';
import { toast } from '@/hooks/useToast';

interface StatusStep { label: string; complete: boolean; failed: boolean; }
interface Comment { message: string; createdAt: string; from: string; }
interface TrackResult {
  id: number;
  reference: string; 
  status: string; 
  statusLabel: string; 
  statusSteps: StatusStep[];
  loanType: string; 
  amount: number; 
  currency: string;
  outstandingBalance?: number;
  totalPaid?: number;
  nextDueDate?: string;
  nextAmountDue?: number;
  submittedDate: string; 
  updatedDate: string; 
  rejectionReason?: string; 
  maritalStatus?: string;
}

export default function TrackPage() {
  const tenant = useTenant();
  const [reference, setReference] = useState('');
  const [phone, setPhone] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState<TrackResult | null>(null);
  const [comments, setComments] = useState<Comment[]>([]);
  const [commentsError, setCommentsError] = useState(false);

  // 💳 Payment Option States
  const [showPaySheet, setShowPaySheet] = useState(false);
  const [momoMethod, setMomoMethod] = useState<'MTN_MOMO' | 'AIRTEL_MONEY'>('MTN_MOMO');
  const [momoPhone, setMomoPhone] = useState('');
  const [paying, setPaying] = useState(false);
  const [paySuccess, setPaySuccess] = useState(false);

  const primary = tenant?.primaryColor ?? '#0D6B3E';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(''); 
    setResult(null); 
    setComments([]); 
    setCommentsError(false); 
    setPaySuccess(false);
    setLoading(true);
    
    try {
      const data = await publicApi.trackApplication(reference.trim(), phone.trim());
      setResult(data as TrackResult);
      
      publicApi.trackComments(reference.trim(), phone.trim())
        .then((c) => setComments(c as Comment[]))
        .catch(() => setCommentsError(true));
    } catch (err: any) {
      setError(err.message || 'We could not find that application matching those parameters.');
    } finally {
      setLoading(false);
    }
  };

  const handleMomoRepayment = async () => {
    if (!result || !momoPhone) return;
    setPaying(true);
    setError('');
    setPaySuccess(false);

    try {
      const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
      const balance = result.outstandingBalance ?? result.amount;
      const dueAmount = result.nextAmountDue && result.nextAmountDue > 0 ? result.nextAmountDue : balance;
      
      const payload = {
        loanId: result.id,
        amount: dueAmount,
        method: momoMethod,
        phoneNumber: momoPhone.trim()
      };
      
      await axios.post(`${API_BASE}/public/initiate-external-payment`, payload);
      setPaySuccess(true);
      setShowPaySheet(false);
      toast('success', 'USSD Push Prompt sent! Confirm the transaction on your screen.');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Mobile money payment request failed.');
    } finally {
      setPaying(false);
    }
  };

  const fmt = (n: number) => (n ?? 0).toLocaleString();
  const fmtDate = (d?: string) => d ? new Date(d).toLocaleDateString('en-RW', { year: 'numeric', month: 'short', day: 'numeric' }) : '—';
  const fmtDateTime = (d?: string) => d ? new Date(d).toLocaleString('en-RW', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—';

  return (
    <div className="bg-gray-50 min-h-screen antialiased">
      <section className="py-16 text-white text-center shadow-inner"
        style={{ background: `linear-gradient(135deg, ${primary} 0%, #0a4a2b 100%)` }}>
        <div className="max-w-2xl mx-auto px-4">
          <h1 className="text-3xl md:text-4xl font-black mb-3 tracking-tight">Public Application Tracking Portal</h1>
          <p className="text-white/80 text-sm">Monitor your current underwriting pipeline steps and execute instant safe repayments securely online.</p>
        </div>
      </section>

      <section className="max-w-lg mx-auto px-4 -mt-10 pb-24 space-y-6">
        <div className="bg-white rounded-xl shadow-xl border border-gray-100 p-6 md:p-8">
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-1.5">Reference Number</label>
              <input
                required value={reference}
                onChange={e => setReference(e.target.value)}
                placeholder="e.g. GFS-2026-000123"
                className="w-full px-4 py-3 border border-gray-300 rounded-lg text-sm font-semibold uppercase focus:outline-none focus:ring-2 focus:ring-emerald-600"
              />
            </div>
            <div>
              <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-1.5">Phone Number</label>
              <input
                required value={phone}
                onChange={e => setPhone(e.target.value)}
                placeholder="Phone number used on application"
                className="w-full px-4 py-3 border border-gray-300 rounded-lg text-sm font-semibold focus:outline-none focus:ring-2 focus:ring-emerald-600"
              />
            </div>
            {error && (
              <div className="bg-red-50 border border-red-200 text-red-700 text-xs rounded-lg px-3 py-2.5 font-semibold">{error}</div>
            )}
            {paySuccess && (
              <div className="bg-green-50 border border-green-200 text-green-800 text-xs rounded-lg px-3 py-2.5 font-bold animate-fadeIn">✓ Repayment request issued! Approve on your mobile handset screen.</div>
            )}
            <button type="submit" disabled={loading}
              className="w-full py-3.5 rounded-xl font-extrabold text-xs text-white shadow-md hover:opacity-95 transition-opacity disabled:opacity-60"
              style={{ backgroundColor: primary }}>
              {loading ? 'Validating Tracker Vault...' : '🔍 Check Application Status'}
            </button>
          </form>
        </div>

       {result && (
          <div className="bg-white rounded-xl shadow-xl border border-gray-100 p-6 md:p-8 animate-fadeIn">
            <div className="flex items-start justify-between mb-6">
              <div>
                <div className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">Reference Code</div>
                <div className="font-mono font-bold text-gray-900 text-sm">{result.reference}</div>
              </div>
              <span className="px-3 py-1.5 rounded-full text-[10px] font-extrabold uppercase tracking-wide"
                style={{ backgroundColor: primary + '15', color: primary }}>
                {result.statusLabel}
              </span>
            </div>

            {/* Progress steps */}
            <div className="flex items-center mb-8">
              {result.statusSteps?.map((step, i) => (
                <div key={step.label} className="flex-1 flex items-center">
                  <div className="flex flex-col items-center flex-1">
                    <div className="w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold text-white shadow-sm"
                      style={{ 
                        backgroundColor: step.failed ? '#dc2626' : step.complete ? primary : '#e5e7eb', 
                        color: step.complete || step.failed ? '#fff' : '#9ca3af' 
                      }}>
                      {step.failed ? '✕' : step.complete ? '✓' : i + 1}
                    </div>
                    <div className="text-[9px] font-bold text-center text-gray-400 mt-1.5 leading-tight px-1 uppercase tracking-tight">{step.label}</div>
                  </div>
                  {i < result.statusSteps.length - 1 && (
                    <div className="h-0.5 flex-1 -mt-5" style={{ backgroundColor: step.complete ? primary : '#e5e7eb' }} />
                  )}
                </div>
              ))}
            </div>

            {result.rejectionReason && (
              <div className="bg-red-50 border border-red-100 text-red-700 text-xs rounded-xl p-3 mb-4 font-medium">
                <strong>Rejection Notice:</strong> {result.rejectionReason}
              </div>
            )}

            <div className="grid grid-cols-2 gap-4 text-xs border-t border-gray-100 pt-4 font-semibold text-gray-600">
              <div><span className="text-gray-400 text-[10px] uppercase font-bold tracking-wider">Approved Principal</span><div className="font-bold text-gray-900 mt-0.5">{result.currency} {fmt(result.amount)}</div></div>
              <div><span className="text-gray-400 text-[10px] uppercase font-bold tracking-wider">Loan Product</span><div className="font-bold text-gray-900 mt-0.5">{result.loanType}</div></div>
              
              {result.status === 'DISBURSED' && (
                <>
                  <div><span className="text-gray-400 text-[10px] uppercase font-bold tracking-wider">Balance Outstanding</span><div className="font-bold text-red-600 mt-0.5">{result.currency} {fmt(result.outstandingBalance ?? result.amount)}</div></div>
                  <div><span className="text-gray-400 text-[10px] uppercase font-bold tracking-wider">Next Settlement Due</span><div className="font-bold text-gray-900 mt-0.5">{result.nextDueDate ? fmtDate(result.nextDueDate) : '—'}</div></div>
                </>
              )}

              <div className="border-t col-span-2 pt-3 flex justify-between text-[11px] text-gray-400">
                <span>Created: {fmtDate(result.submittedDate)}</span>
                <span>Updated: {fmtDate(result.updatedDate)}</span>
              </div>
            </div>

            {/* ⚡ PAYMENT INITIATION KEY */}
            {result.status === 'DISBURSED' && (result.outstandingBalance ?? 1) > 0 && (
              <div className="mt-4 pt-4 border-t border-gray-100">
                <button
                  type="button"
                  onClick={() => { setShowPaySheet(true); setPaySuccess(false); }}
                  className="w-full bg-emerald-700 hover:bg-emerald-800 text-white text-xs font-extrabold py-3 rounded-xl transition-all shadow-md active:scale-[0.99]"
                >
                  💳 Pay Outstanding Installment
                </button>
              </div>
            )}
          </div>
        )}

        {/* 💬 AUDIT FEED MESSAGE THREAD BOX */}
        {result && (
          <div className="mt-6 bg-white rounded-xl shadow-xl border border-gray-100 p-6 md:p-8 animate-fadeIn">
            <h3 className="font-bold text-gray-900 text-sm mb-0.5">Updates from {tenant?.name ?? 'our team'}</h3>
            <p className="text-xs text-gray-400 mb-4">Messages from your loan officer, including any additional document feedback.</p>
            
            {commentsError && (
              <p className="text-xs text-amber-700 bg-amber-50 border border-amber-100 rounded-lg p-3 font-semibold">
                Could not stream server notification feeds. Refresh the canvas.
              </p>
            )}
            
            {!commentsError && comments.length === 0 && (
              <p className="text-xs text-gray-400 italic">No formal status comment markers logged yet.</p>
            )}
            
            {!commentsError && comments.length > 0 && (
              <div className="space-y-4">
                {comments.map((c, i) => (
                  <div key={i} className="border-l-2 pl-4 py-1 border-emerald-500 bg-gray-50/50 rounded-r-xl p-3">
                    <p className="text-xs font-bold text-gray-800 leading-relaxed">{c.message}</p>
                    <div className="text-[10px] text-gray-400 font-semibold mt-1.5 flex justify-between">
                      <span>By: {c.from || 'Loan Officer'}</span>
                      <span>{fmtDateTime(c.createdAt)}</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* 🟨 MOBILE MONEY PUSH SLIDER MODAL DRAWER */}
        {showPaySheet && result && (
          <div className="fixed inset-0 bg-black/70 z-50 flex items-center justify-center p-4 backdrop-blur-sm animate-fadeIn">
            <div className="bg-white rounded-2xl max-w-sm w-full overflow-hidden shadow-2xl border border-gray-100">
              <div className="px-6 py-4 bg-gray-50 border-b border-gray-100 flex items-center justify-between">
                <h3 className="font-black text-gray-900 text-xs uppercase tracking-wider">Select Pay Channel</h3>
                <button 
                  type="button"
                  onClick={() => setShowPaySheet(false)} 
                  className="text-gray-400 hover:text-gray-700 font-bold text-xl"
                >
                  ×
                </button>
              </div>
              
              <div className="p-6 space-y-4">
                <div className="grid grid-cols-2 gap-2">
                  <button 
                    type="button" 
                    onClick={() => setMomoMethod('MTN_MOMO')}
                    className={`p-3 border rounded-xl flex flex-col items-center gap-1.5 font-bold text-xs transition-colors ${
                      momoMethod === 'MTN_MOMO' ? 'border-amber-400 bg-amber-50/30 text-amber-900' : 'border-gray-200 bg-white'
                    }`}
                  >
                    🟨 MTN MoMo
                  </button>
                  
                  <button 
                    type="button" 
                    onClick={() => setMomoMethod('AIRTEL_MONEY')}
                    className={`p-3 border rounded-xl flex flex-col items-center gap-1.5 font-bold text-xs transition-colors ${
                      momoMethod === 'AIRTEL_MONEY' ? 'border-red-400 bg-red-50/30 text-red-900' : 'border-gray-200 bg-white'
                    }`}
                  >
                    🟥 Airtel Money
                  </button>
                </div>
                
                <div>
                  <label className="text-[10px] font-bold text-gray-400 uppercase tracking-wider block">Momo Wallet Number</label>
                  <input
                    type="tel" 
                    value={momoPhone} 
                    onChange={(e) => setMomoPhone(e.target.value)} 
                    placeholder="07XXXXXXXX" 
                    required
                    className="w-full border border-gray-300 rounded-xl px-3 py-2.5 text-xs font-semibold mt-1 focus:ring-2 focus:ring-emerald-600 focus:outline-none font-mono"
                  />
                </div>
                
                <button
                  type="button" 
                  onClick={handleMomoRepayment} 
                  disabled={paying || !momoPhone}
                  className="w-full bg-[#0D6B3E] hover:bg-emerald-800 text-white font-extrabold text-xs py-3.5 rounded-xl shadow-md transition-all disabled:opacity-40"
                >
                  {paying ? 'Pushing Network Prompts...' : '⚡ Confirm Repayment Push'}
                </button>
              </div>
            </div>
          </div>
        )}

      </section>
    </div>
  );
}