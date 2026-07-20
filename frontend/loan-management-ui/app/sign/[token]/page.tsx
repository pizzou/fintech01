'use client';
import { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import axios from 'axios';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

interface SignView {
  status: 'PENDING' | 'OTP_SENT' | 'SIGNED' | 'DECLINED' | 'EXPIRED';
  documentType: string;
  documentText: string;
  consentText: string;
  borrowerName?: string;
  expiresAt?: string;
  signedAt?: string;
}

export default function SigningPage() {
  const params = useParams();
  const token = Array.isArray(params?.token) ? params.token[0] : (params?.token as string);

  const [view, setView] = useState<SignView | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [otp, setOtp] = useState('');
  const [fullName, setFullName] = useState('');
  const [busy, setBusy] = useState(false);
  const [resending, setResending] = useState(false);
  const [declining, setDeclining] = useState(false);
  const [success, setSuccess] = useState(false);

  const load = () => {
    setLoading(true); setError('');
    axios.get(`${API_BASE}/public/esignature/${token}`)
      .then((r) => setView(r.data?.data ?? r.data))
      .catch((e) => setError(e.response?.data?.error || e.response?.data?.message || 'This signing link could not be found or has expired.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { if (token) load(); }, [token]);

  const handleResend = async () => {
    setResending(true); setError('');
    try { await axios.post(`${API_BASE}/public/esignature/${token}/resend-otp`); }
    catch (e: any) { setError(e.response?.data?.error || e.response?.data?.message || 'Could not resend code'); }
    setResending(false);
  };

  const handleSign = async () => {
    setBusy(true); setError('');
    try {
      await axios.post(`${API_BASE}/public/esignature/${token}/sign`, { otp, fullName });
      setSuccess(true);
    } catch (e: any) { setError(e.response?.data?.error || e.response?.data?.message || 'Could not verify — please check the code and try again.'); }
    setBusy(false);
  };

  const handleDecline = async () => {
    if (!confirm('Are you sure you want to decline signing this agreement?')) return;
    setDeclining(true); setError('');
    try { await axios.post(`${API_BASE}/public/esignature/${token}/decline`, { reason: 'Declined by borrower' }); load(); }
    catch (e: any) { setError(e.response?.data?.error || e.response?.data?.message || 'Could not record decline'); }
    setDeclining(false);
  };

  if (loading) {
    return <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="w-8 h-8 border-2 border-teal-500 border-t-transparent rounded-full animate-spin" />
    </div>;
  }

  if (error && !view) {
    return <div className="min-h-screen flex items-center justify-center bg-gray-50 p-6">
      <div className="bg-white rounded-2xl border border-gray-200 p-8 max-w-md text-center">
        <div className="text-3xl mb-3">⚠️</div>
        <h1 className="font-bold text-gray-900 mb-2">Link unavailable</h1>
        <p className="text-sm text-gray-500">{error}</p>
      </div>
    </div>;
  }

  const alreadyDone = view?.status === 'SIGNED' || success;
  const declined = view?.status === 'DECLINED';
  const expired = view?.status === 'EXPIRED';

  return (
    <div className="min-h-screen bg-gray-50 py-10 px-4">
      <div className="max-w-2xl mx-auto">
        <div className="text-center mb-6">
          <div className="inline-flex w-12 h-12 bg-[#0D6B3E] rounded-xl items-center justify-center text-white font-black text-lg mb-3">✍️</div>
          <h1 className="text-xl font-extrabold text-gray-900">Sign Your Loan Agreement</h1>
          <p className="text-sm text-gray-500 mt-1">Secure electronic signature — {view?.documentType?.replace(/_/g, ' ')}</p>
        </div>

        {alreadyDone ? (
          <div className="bg-white rounded-2xl border border-gray-200 p-8 text-center">
            <div className="text-4xl mb-3">✅</div>
            <h2 className="font-bold text-gray-900 mb-1">Document signed</h2>
            <p className="text-sm text-gray-500">Thank you{view?.borrowerName ? `, ${view.borrowerName}` : ''}. Your signed agreement has been recorded and your loan officer has been notified.</p>
          </div>
        ) : declined ? (
          <div className="bg-white rounded-2xl border border-gray-200 p-8 text-center">
            <div className="text-4xl mb-3">🚫</div>
            <h2 className="font-bold text-gray-900 mb-1">Signing declined</h2>
            <p className="text-sm text-gray-500">You've declined to sign this agreement. Your loan officer will be in touch.</p>
          </div>
        ) : expired ? (
          <div className="bg-white rounded-2xl border border-gray-200 p-8 text-center">
            <div className="text-4xl mb-3">⏰</div>
            <h2 className="font-bold text-gray-900 mb-1">Link expired</h2>
            <p className="text-sm text-gray-500">This signing link has expired. Please ask your loan officer to send a new one.</p>
          </div>
        ) : (
          <>
            <div className="bg-white rounded-2xl border border-gray-200 p-6 mb-4">
              <h2 className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-2">Agreement</h2>
              <pre className="whitespace-pre-wrap text-sm text-gray-700 font-sans leading-relaxed max-h-80 overflow-y-auto border border-gray-100 rounded-lg p-4 bg-gray-50">
                {view?.documentText}
              </pre>
            </div>

            <div className="bg-white rounded-2xl border border-gray-200 p-6 space-y-4">
              <p className="text-xs text-gray-500 leading-relaxed">{view?.consentText}</p>

              {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-lg px-3 py-2">{error}</div>}

              <div>
                <label className="text-xs font-semibold text-gray-600">Type your full legal name</label>
                <input value={fullName} onChange={(e) => setFullName(e.target.value)} placeholder="e.g. Jean Uwimana"
                  className="w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm mt-1 focus:outline-none focus:ring-2 focus:ring-teal-500" />
              </div>
              <div>
                <label className="text-xs font-semibold text-gray-600">Verification code (sent by SMS)</label>
                <input value={otp} onChange={(e) => setOtp(e.target.value)} maxLength={6} placeholder="6-digit code"
                  className="w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm mt-1 tracking-widest font-mono focus:outline-none focus:ring-2 focus:ring-teal-500" />
                <button onClick={handleResend} disabled={resending} className="text-xs text-teal-600 hover:underline mt-1.5">
                  {resending ? 'Resending…' : "Didn't get a code? Resend"}
                </button>
              </div>

              <div className="flex gap-3 pt-2">
                <button onClick={handleSign} disabled={busy || !otp || fullName.trim().length < 3}
                  className="flex-1 bg-[#0D6B3E] hover:opacity-90 text-white rounded-lg py-3 text-sm font-bold disabled:opacity-50">
                  {busy ? 'Signing…' : '✓ I Agree — Sign Document'}
                </button>
                <button onClick={handleDecline} disabled={declining}
                  className="border border-gray-300 text-gray-600 rounded-lg py-3 px-5 text-sm font-medium hover:bg-gray-50">
                  Decline
                </button>
              </div>
            </div>
          </>
        )}

        <p className="text-center text-xs text-gray-400 mt-6">Protected electronic signature · IP address and timestamp recorded for verification</p>
      </div>
    </div>
  );
}
