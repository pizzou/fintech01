'use client';
import { useEffect, useState } from 'react';
import { getDashboardStats } from '../../../services/dashboardService';
import { getLoans } from '../../../services/loanService';
import { getBorrowers } from '../../../services/borrowerService';
import { getOverduePayments } from '../../../services/paymentService';
import { DashboardStats, Loan, Borrower, Payment } from '../../../types/index';
import { PageSpinner } from '../../../components/ui/Skeleton';

export default function AiAnalysisPage() {
  const [stats,     setStats]     = useState<DashboardStats | null>(null);
  const [loans,     setLoans]     = useState<Loan[]>([]);
  const [borrowers, setBorrowers] = useState<Borrower[]>([]);
  const [overdue,   setOverdue]   = useState<Payment[]>([]);
  const [analysis,  setAnalysis]  = useState('');
  const [loading,   setLoading]   = useState(true);
  const [analyzing, setAnalyzing] = useState(false);
  const [question,  setQuestion]  = useState('');
  const [asking,    setAsking]    = useState(false);
  const [answer,    setAnswer]    = useState('');

  useEffect(() => {
    Promise.all([getDashboardStats(), getLoans(), getBorrowers(), getOverduePayments()])
      .then(([s, l, b, o]) => {
        setStats(s as DashboardStats);
        setLoans(l as Loan[]);
        setBorrowers(b as Borrower[]);
        setOverdue(o as Payment[]);
      })
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const buildContext = () => {
    if (!stats) return '';
    const collRate = stats.totalAmountLent > 0
      ? ((stats.paymentsCollected / stats.totalAmountLent) * 100).toFixed(1) : '0';
    const highRisk = loans.filter(l => l.riskCategory === 'HIGH' || l.riskCategory === 'CRITICAL').length;
    const verifiedBorrowers = borrowers.filter(b => b.kycStatus === 'VERIFIED').length;
    const avgCredit = borrowers.filter(b => b.creditScore != null).length > 0
      ? Math.round(borrowers.reduce((s, b) => s + (b.creditScore ?? 0), 0) / borrowers.filter(b => b.creditScore != null).length)
      : 'N/A';

    return `
LOAN PORTFOLIO DATA:
- Total borrowers: ${stats.totalBorrowers} (${verifiedBorrowers} KYC verified)
- Average credit score: ${avgCredit}
- Total loans: ${stats.totalLoans} | Active: ${stats.activeLoans} | Pending: ${stats.pendingLoans}
- Rejected loans: ${stats.rejectedLoans} | Fully repaid: ${stats.closedLoans}
- Total disbursed: $${stats.totalAmountLent.toLocaleString()}
- Total collected: $${stats.paymentsCollected.toLocaleString()} (${collRate}% collection rate)
- Penalties collected: $${stats.penaltiesCollected.toLocaleString()}
- Overdue payments: ${stats.overduePayments}
- High/Critical risk loans: ${highRisk}
    `.trim();
  };

  const runAnalysis = async () => {
    setAnalyzing(true);
    setAnalysis('');
    try {
      const context = buildContext();
      const response = await fetch('https://api.anthropic.com/v1/messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          model: 'claude-sonnet-4-20250514',
          max_tokens: 1000,
          messages: [{
            role: 'user',
            content: `You are a fintech portfolio analyst. Analyze this microfinance loan portfolio and provide actionable insights, risk assessment, and recommendations. Be specific and use the data provided.\n\n${context}\n\nProvide:\n1. Portfolio Health Score (0-100) with explanation\n2. Top 3 risks and how to mitigate them\n3. Top 3 growth opportunities\n4. Specific actions to take this week\n\nBe concise and practical.`
          }]
        })
      });
      const data = await response.json();
      const text = data.content?.map((c: { type: string; text?: string }) => c.type === 'text' ? c.text : '').join('') || 'Analysis failed.';
      setAnalysis(text);
    } catch {
      setAnalysis('Could not generate analysis. Please try again.');
    } finally {
      setAnalyzing(false);
    }
  };

  const askQuestion = async () => {
    if (!question.trim()) return;
    setAsking(true);
    setAnswer('');
    try {
      const context = buildContext();
      const response = await fetch('https://api.anthropic.com/v1/messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          model: 'claude-sonnet-4-20250514',
          max_tokens: 500,
          messages: [{
            role: 'user',
            content: `You are a fintech portfolio analyst. Here is the portfolio data:\n\n${context}\n\nQuestion: ${question}\n\nAnswer concisely and practically.`
          }]
        })
      });
      const data = await response.json();
      const text = data.content?.map((c: { type: string; text?: string }) => c.type === 'text' ? c.text : '').join('') || 'Could not answer.';
      setAnswer(text);
    } catch {
      setAnswer('Could not get an answer. Please try again.');
    } finally {
      setAsking(false);
    }
  };

  if (loading) return <PageSpinner />;

  const collRate = stats && stats.totalAmountLent > 0
    ? ((stats.paymentsCollected / stats.totalAmountLent) * 100).toFixed(1) : '0';
  const highRisk = loans.filter(l => l.riskCategory === 'HIGH' || l.riskCategory === 'CRITICAL').length;

  return (
    <div className="space-y-6 max-w-4xl">
      <div>
        <h1 className="text-xl font-bold text-gray-900">AI Portfolio Analysis</h1>
        <p className="text-sm text-gray-500">Powered by AI — get deep insights into your loan portfolio</p>
      </div>

      {/* Portfolio snapshot */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {[
          { label: 'Collection Rate', value: collRate + '%',                      color: parseFloat(collRate) >= 80 ? 'text-green-600' : 'text-red-600' },
          { label: 'Active Loans',    value: String(stats?.activeLoans ?? 0),     color: 'text-blue-600'   },
          { label: 'Overdue',         value: String(stats?.overduePayments ?? 0), color: 'text-red-600'    },
          { label: 'High Risk',       value: String(highRisk),                    color: 'text-orange-600' },
        ].map(s => (
          <div key={s.label} className="bg-white rounded-2xl border border-gray-100 p-4">
            <p className="text-gray-400 text-xs mb-1">{s.label}</p>
            <p className={`text-xl font-bold ${s.color}`}>{s.value}</p>
          </div>
        ))}
      </div>

      {/* AI Analysis */}
      <div className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-2xl p-6 text-white">
        <div className="flex items-center gap-3 mb-5">
          <div className="w-8 h-8 rounded-xl bg-green-500 flex items-center justify-center text-sm font-bold">✦</div>
          <div>
            <h2 className="font-bold text-sm">Full Portfolio Analysis</h2>
            <p className="text-slate-400 text-xs">AI-powered deep dive into your portfolio performance</p>
          </div>
          <button onClick={runAnalysis} disabled={analyzing}
            className="ml-auto bg-green-500 hover:bg-green-600 disabled:opacity-60 text-white text-xs font-semibold px-4 py-2 rounded-xl transition">
            {analyzing ? 'Analyzing...' : analysis ? 'Re-analyze' : '✦ Analyze Now'}
          </button>
        </div>

        {analyzing && (
          <div className="flex items-center gap-3 py-8 justify-center">
            <div className="w-5 h-5 border-2 border-green-400 border-t-transparent rounded-full animate-spin" />
            <p className="text-slate-300 text-sm">Analyzing your portfolio...</p>
          </div>
        )}

        {!analyzing && !analysis && (
          <div className="text-center py-8">
            <p className="text-3xl mb-3">🤖</p>
            <p className="text-slate-300 text-sm">Click "Analyze Now" to get AI-powered insights about your portfolio health, risks, and opportunities.</p>
          </div>
        )}

        {analysis && (
          <div className="bg-slate-700/50 rounded-xl p-4 text-slate-200 text-sm leading-relaxed whitespace-pre-wrap">
            {analysis}
          </div>
        )}
      </div>

      {/* Ask a question */}
      <div className="bg-white rounded-2xl border border-gray-100 p-6">
        <h2 className="font-semibold text-gray-800 text-sm mb-4">Ask Your Portfolio Assistant</h2>
        <div className="space-y-3">
          <div className="flex gap-2">
            <input
              value={question}
              onChange={e => setQuestion(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && !asking && askQuestion()}
              placeholder="e.g. Which borrowers are at highest risk of default? How can I improve collection rate?"
              className="flex-1 border border-gray-200 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-green-400"
            />
            <button onClick={askQuestion} disabled={asking || !question.trim()}
              className="bg-green-500 hover:bg-green-600 disabled:opacity-60 text-white text-sm font-semibold px-5 py-2.5 rounded-xl transition flex-shrink-0">
              {asking ? '...' : 'Ask'}
            </button>
          </div>

          {/* Quick question chips */}
          <div className="flex flex-wrap gap-2">
            {[
              'How can I improve collection rate?',
              'What are my biggest risks?',
              'Which loans need urgent attention?',
              'How is my portfolio performing?',
            ].map(q => (
              <button key={q} onClick={() => setQuestion(q)}
                className="text-xs text-gray-500 border border-gray-200 bg-gray-50 hover:bg-gray-100 px-3 py-1.5 rounded-xl transition">
                {q}
              </button>
            ))}
          </div>

          {asking && (
            <div className="flex items-center gap-2 py-2">
              <div className="w-4 h-4 border-2 border-green-400 border-t-transparent rounded-full animate-spin" />
              <p className="text-gray-400 text-sm">Getting answer...</p>
            </div>
          )}

          {answer && (
            <div className="bg-green-50 border border-green-100 rounded-xl p-4 text-sm text-gray-700 leading-relaxed whitespace-pre-wrap">
              <div className="flex items-center gap-2 mb-2">
                <span className="text-green-600 font-semibold text-xs">✦ AI Answer</span>
              </div>
              {answer}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
