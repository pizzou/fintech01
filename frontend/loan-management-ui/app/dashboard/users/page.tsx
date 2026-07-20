'use client';
import { useEffect, useState } from 'react';
import { get, post, put, del } from '../../../services/api';
import { toast } from '../../../hooks/useToast';
import { hasRole } from '../../../services/authService';
import { PageSpinner } from '../../../components/ui/Skeleton';

interface UserRow {
  id: number;
  name: string;
  email: string;
  role?: { id: number; name: string };
  organization?: { id: number; name: string };
}

const ROLE_BADGE: Record<string, string> = {
  ADMIN:        'bg-purple-100 text-purple-700',
  LOAN_OFFICER: 'bg-blue-100 text-blue-700',
  MANAGER:      'bg-green-100 text-green-700',
  ACCOUNTANT:   'bg-orange-100 text-orange-700',
};

const ROLES = ['ADMIN', 'MANAGER', 'LOAN_OFFICER', 'ACCOUNTANT'];

function inputCls(err?: string) {
  return 'w-full border rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 ' +
    (err ? 'border-red-400 focus:ring-red-400 bg-red-50' : 'border-gray-300 focus:ring-blue-500');
}

export default function UsersPage() {
  const isAdmin = hasRole('ADMIN');
  const [users,       setUsers]       = useState<UserRow[]>([]);
  const [loading,     setLoading]     = useState(true);
  const [showCreate,  setShowCreate]  = useState(false);
  const [resetUserId, setResetUserId] = useState<number | null>(null);
  const [delUserId,   setDelUserId]   = useState<number | null>(null);

  const [name,      setName]      = useState('');
  const [email,     setEmail]     = useState('');
  const [password,  setPassword]  = useState('');
  const [role,      setRole]      = useState('LOAN_OFFICER');
  const [saving,    setSaving]    = useState(false);
  const [errors,    setErrors]    = useState<Record<string, string>>({});

  const [newPw,     setNewPw]     = useState('');
  const [cfPw,      setCfPw]      = useState('');
  const [resetting, setResetting] = useState(false);

  const getMsg = (err: unknown) => err instanceof Error ? err.message : 'Something went wrong';

  const handleRoleChange = async (userId: number, newRole: string) => {
    try {
      await put(`/users/${userId}/role`, { role: newRole });
      toast('success', `Role updated to ${newRole.replace('_', ' ')}`);
      load();
    } catch (err) { toast('error', getMsg(err)); }
  };

  const load = () => {
    setLoading(true);
    get('/users')
      .then(d => setUsers(d as UserRow[]))
      .catch(console.error)
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const validate = () => {
    const e: Record<string, string> = {};
    if (!name.trim()) e.name = 'Name is required';
    if (!email.trim()) e.email = 'Email is required';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) e.email = 'Invalid email';
    if (!password) e.password = 'Password is required';
    else if (password.length < 6) e.password = 'Min 6 characters';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;
    setSaving(true);
    try {
      await post('/users', { name, email, password, role });
      toast('success', name + ' created successfully');
      setName(''); setEmail(''); setPassword(''); setRole('LOAN_OFFICER');
      setShowCreate(false);
      load();
    } catch (err: unknown) {
      toast('error', getMsg(err));
    } finally { setSaving(false); }
  };

  const handleResetPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newPw || newPw.length < 6) { toast('error', 'Min 6 characters'); return; }
    if (newPw !== cfPw) { toast('error', 'Passwords do not match'); return; }
    setResetting(true);
    try {
      await post('/users/' + resetUserId + '/reset-password', { newPassword: newPw });
      toast('success', 'Password reset successfully');
      setResetUserId(null); setNewPw(''); setCfPw('');
    } catch (err: unknown) {
      toast('error', getMsg(err));
    } finally { setResetting(false); }
  };

  const handleDelete = async () => {
    if (!delUserId) return;
    try {
      await del('/users/' + delUserId);
      toast('success', 'User deleted');
      setDelUserId(null);
      load();
    } catch (err: unknown) {
      toast('error', getMsg(err));
    }
  };

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-gray-900">Users</h1>
          <p className="text-sm text-gray-500">{users.length} team members</p>
        </div>
        {isAdmin && (
          <button
            onClick={() => setShowCreate(true)}
            className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition"
          >
            + Add User
          </button>
        )}
      </div>

      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
        {loading ? <PageSpinner /> : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
              <tr>
                {['User', 'Email', 'Role', 'Organization', isAdmin ? 'Actions' : '']
                  .filter(Boolean).map(h => (
                  <th key={h} className="px-5 py-3 text-left font-medium">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {users.length === 0 && (
                <tr><td colSpan={5} className="text-center py-10 text-gray-400 text-sm">No users found</td></tr>
              )}
              {users.map(u => (
                <tr key={u.id} className="hover:bg-gray-50">
                  <td className="px-5 py-4">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-full bg-indigo-100 text-indigo-700 flex items-center justify-center text-xs font-bold flex-shrink-0">
                        {u.name?.[0]?.toUpperCase() ?? '?'}
                      </div>
                      <span className="font-medium text-gray-800">{u.name ?? '—'}</span>
                    </div>
                  </td>
                  <td className="px-5 py-4 text-gray-500">{u.email}</td>
                  <td className="px-5 py-4">
                    {isAdmin ? (
                      <select
                        value={u.role?.name ?? ''}
                        onChange={(e) => handleRoleChange(u.id, e.target.value)}
                        className={'px-2 py-1 rounded-full text-xs font-medium border-0 cursor-pointer ' +
                          (ROLE_BADGE[u.role?.name ?? ''] ?? 'bg-gray-100 text-gray-600')}
                      >
                        {ROLES.map(r => <option key={r} value={r}>{r.replace('_', ' ')}</option>)}
                      </select>
                    ) : (
                      <span className={'px-2.5 py-1 rounded-full text-xs font-medium ' +
                        (ROLE_BADGE[u.role?.name ?? ''] ?? 'bg-gray-100 text-gray-600')}>
                        {u.role?.name ?? '—'}
                      </span>
                    )}
                  </td>
                  <td className="px-5 py-4 text-gray-500">{u.organization?.name ?? '—'}</td>
                  {isAdmin && (
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-2">
                        <button
                          onClick={() => { setResetUserId(u.id); setNewPw(''); setCfPw(''); }}
                          className="text-xs text-blue-600 hover:text-blue-800 font-medium border border-blue-200 bg-blue-50 hover:bg-blue-100 px-2.5 py-1 rounded-lg transition"
                        >
                          Reset PW
                        </button>
                        <button
                          onClick={() => setDelUserId(u.id)}
                          className="text-xs text-red-500 hover:text-red-700 font-medium border border-red-200 bg-red-50 hover:bg-red-100 px-2.5 py-1 rounded-lg transition"
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {showCreate && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl w-full max-w-md shadow-2xl">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-800">Add New User</h2>
              <button onClick={() => setShowCreate(false)}
                className="text-gray-400 hover:text-gray-600 text-xl leading-none">x</button>
            </div>
            <form onSubmit={handleCreate} className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Full Name *</label>
                <input value={name} onChange={e => setName(e.target.value)}
                  placeholder="John Doe" className={inputCls(errors.name)} />
                {errors.name && <p className="text-xs text-red-600 mt-1">{errors.name}</p>}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Email *</label>
                <input type="email" value={email} onChange={e => setEmail(e.target.value)}
                  placeholder="john@company.com" className={inputCls(errors.email)} />
                {errors.email && <p className="text-xs text-red-600 mt-1">{errors.email}</p>}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Password *</label>
                <input type="password" value={password} onChange={e => setPassword(e.target.value)}
                  placeholder="Min 6 characters" className={inputCls(errors.password)} />
                {errors.password && <p className="text-xs text-red-600 mt-1">{errors.password}</p>}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Role *</label>
                <select value={role} onChange={e => setRole(e.target.value)}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                  {ROLES.map(r => <option key={r} value={r}>{r.replace('_', ' ')}</option>)}
                </select>
              </div>
              <div className="flex gap-3 pt-2">
                <button type="submit" disabled={saving}
                  className="flex-1 bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white py-2.5 rounded-lg text-sm font-medium transition">
                  {saving ? 'Creating...' : 'Create User'}
                </button>
                <button type="button" onClick={() => setShowCreate(false)}
                  className="flex-1 border border-gray-300 hover:bg-gray-50 text-gray-700 py-2.5 rounded-lg text-sm font-medium transition">
                  Cancel
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {resetUserId !== null && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl w-full max-w-sm shadow-2xl">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-800">Reset Password</h2>
              <button onClick={() => setResetUserId(null)}
                className="text-gray-400 hover:text-gray-600 text-xl leading-none">x</button>
            </div>
            <form onSubmit={handleResetPassword} className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">New Password</label>
                <input type="password" value={newPw} onChange={e => setNewPw(e.target.value)}
                  placeholder="Min 6 characters" className={inputCls()} />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Confirm Password</label>
                <input type="password" value={cfPw} onChange={e => setCfPw(e.target.value)}
                  placeholder="Repeat password" className={inputCls()} />
              </div>
              <div className="flex gap-3 pt-2">
                <button type="submit" disabled={resetting}
                  className="flex-1 bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white py-2.5 rounded-lg text-sm font-medium transition">
                  {resetting ? 'Resetting...' : 'Reset Password'}
                </button>
                <button type="button" onClick={() => setResetUserId(null)}
                  className="flex-1 border border-gray-300 hover:bg-gray-50 text-gray-700 py-2.5 rounded-lg text-sm font-medium transition">
                  Cancel
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {delUserId !== null && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl w-full max-w-sm shadow-2xl p-6 text-center">
            <div className="w-14 h-14 bg-red-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <svg className="w-7 h-7 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M12 9v2m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
            <h2 className="text-lg font-semibold text-gray-800 mb-2">Delete User?</h2>
            <p className="text-sm text-gray-500 mb-6">This action cannot be undone.</p>
            <div className="flex gap-3">
              <button onClick={handleDelete}
                className="flex-1 bg-red-600 hover:bg-red-700 text-white py-2.5 rounded-lg text-sm font-medium transition">
                Delete
              </button>
              <button onClick={() => setDelUserId(null)}
                className="flex-1 border border-gray-300 hover:bg-gray-50 text-gray-700 py-2.5 rounded-lg text-sm font-medium transition">
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}