'use client';
import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Sidebar from '@/components/Sidebar';
import { AuthContext, useAuthState } from '@/hooks/useAuth';
import { ToastContainer } from '@/components/ui/ToastContainer';
import { OfflineProvider } from '@/components/OfflineProvider';

const authHeader = (): Record<string, string> => {
  if (typeof window === 'undefined') return {};
  const token = localStorage.getItem('token');
  return token ? { Authorization: `Bearer ${token}` } : {};
};

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const auth  = useAuthState();
  const router = useRouter();

  useEffect(() => {
    if (!auth.loading && !auth.user) router.replace('/login');
  }, [auth.loading, auth.user, router]);

  if (auth.loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="flex flex-col items-center gap-3">
          <div className="w-10 h-10 border-3 border-teal-500 border-t-transparent rounded-full animate-spin" />
          <p className="text-sm text-gray-500 font-medium">Loading LoanSaaS Pro…</p>
        </div>
      </div>
    );
  }

  if (!auth.user) return null;

  return (
    <AuthContext.Provider value={auth}>
      <OfflineProvider authHeader={authHeader} />
      <div className="flex min-h-screen bg-gray-50">
        <Sidebar />
        <main className="flex-1 ml-64 flex flex-col min-h-screen">
          <div className="flex-1 p-7">{children}</div>
        </main>
      </div>
      <ToastContainer />
    </AuthContext.Provider>
  );
}