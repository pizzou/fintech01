'use client';
import { useState, useEffect, createContext, useContext } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { OfflineProvider } from '../../components/OfflineProvider';
import { ToastContainer } from '../../components/ui/ToastContainer';
import { TENANT_SLUG } from '../../lib/tenant';

interface TenantConfig {
  name: string;
  slug: string;
  country: string;
  currency: string;
  primaryColor: string;
  accentColor: string;
  logoUrl?: string;
  contactEmail?: string;
  contactPhone?: string;
  website?: string;
  address?: string;
  tagline?: string;
  mission?: string;
  vision?: string;
  founded?: string;
  registrationNumber?: string;
  socialMedia?: { facebook?: string; instagram?: string; linkedin?: string; twitter?: string; whatsapp?: string };
  mapUrl?: string;
  services?: { title: string; description: string; icon: string; rate: string; maxAmount: string; term: string }[];
  hero?: { headline: string; subtext: string };
  stats?: { icon: string; value: string; label: string }[];
  testimonials?: { name: string; role: string; text: string; rating: number }[];
  team?: { name: string; role: string; initials: string }[];
}

const TenantCtx = createContext<TenantConfig | null>(null);
export const useTenant = () => useContext(TenantCtx);

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

/** Shown only while the one configured institution's profile is loading. */
const FALLBACK_TENANT: TenantConfig = {
  name: "Loading...", slug: TENANT_SLUG, country: "Rwanda", currency: "RWF",
  primaryColor: "#0D6B3E", accentColor: "#F5A623", services: [],
};

function IconPhone() {
  return <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z"/></svg>;
}
function IconMail() {
  return <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M4 4h16v16H4z" opacity="0"/><path d="M22 6c0-1.1-.9-2-2-2H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6z"/><path d="m22 6-10 7L2 6"/></svg>;
}
function IconShield() {
  return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="m9 12 2 2 4-4"/></svg>;
}

function IconFacebook() {
  return <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor"><path d="M22 12c0-5.52-4.48-10-10-10S2 6.48 2 12c0 4.84 3.44 8.87 8 9.8V15H8v-3h2V9.5C10 7.57 11.57 6 13.5 6H16v3h-2c-.55 0-1 .45-1 1v2h3v3h-3v6.95c5.05-.5 9-4.76 9-9.95z"/></svg>;
}
function IconInstagram() {
  return <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="2" y="2" width="20" height="20" rx="5"/><circle cx="12" cy="12" r="4"/><circle cx="17.5" cy="6.5" r="1" fill="currentColor" stroke="none"/></svg>;
}
function IconLinkedin() {
  return <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor"><path d="M20.45 20.45h-3.55v-5.57c0-1.33-.02-3.03-1.85-3.03-1.86 0-2.14 1.45-2.14 2.94v5.66H9.36V9h3.41v1.56h.05c.47-.9 1.63-1.85 3.36-1.85 3.6 0 4.27 2.37 4.27 5.45v6.29zM5.34 7.43a2.06 2.06 0 1 1 0-4.12 2.06 2.06 0 0 1 0 4.12zM7.11 20.45H3.56V9h3.55v11.45z"/></svg>;
}
function IconTwitter() {
  return <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor"><path d="M18.9 2H22l-7.6 8.7L23.3 22h-7l-5.5-7.2L4.4 22H1.3l8.1-9.3L1 2h7.2l5 6.6L18.9 2zm-1.2 18h1.7L7.4 4H5.6l12.1 16z"/></svg>;
}
function IconWhatsapp() {
  return <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor"><path d="M12.04 2C6.58 2 2.13 6.45 2.13 11.91c0 1.75.46 3.45 1.32 4.95L2.05 22l5.25-1.38a9.87 9.87 0 0 0 4.74 1.21h.01c5.46 0 9.9-4.45 9.9-9.91C21.96 6.45 17.5 2 12.04 2zm5.8 14.06c-.24.68-1.4 1.32-1.93 1.4-.5.08-1.11.11-1.79-.11-.41-.13-.94-.31-1.62-.6-2.85-1.23-4.7-4.1-4.85-4.29-.14-.19-1.16-1.55-1.16-2.95 0-1.41.73-2.1.99-2.39.26-.29.57-.36.76-.36.19 0 .38 0 .55.01.18.01.41-.07.64.49.24.58.81 2 .88 2.14.07.14.12.31.02.5-.09.19-.14.31-.28.48-.14.17-.29.37-.42.5-.14.14-.28.29-.12.57.16.28.71 1.17 1.52 1.89 1.05.93 1.93 1.22 2.21 1.36.28.14.44.12.61-.07.16-.19.69-.8.87-1.08.18-.28.36-.23.6-.14.24.09 1.53.72 1.79.85.26.14.44.2.5.32.06.11.06.65-.18 1.33z"/></svg>;
}

export default function SiteLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const slug = TENANT_SLUG;
  const [tenant, setTenant]   = useState<TenantConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setNotFound(false);
    fetch(`${API_BASE}/public/tenant/${slug}`)
      .then(r => r.json())
      .then((configRes) => {
        if (cancelled) return;
        const data = configRes?.data;
        if (!data || configRes?.success === false) { setNotFound(true); return; }
        setTenant({ ...FALLBACK_TENANT, ...data, slug });
      })
      .catch(() => { if (!cancelled) setNotFound(true); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [slug]);

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-white">
        <div className="w-8 h-8 border-2 border-[#0D6B3E] border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (notFound || !tenant) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 p-6">
        <div className="bg-white rounded-lg border border-gray-200 p-8 max-w-md text-center">
          <h1 className="font-bold text-gray-900 mb-2">Site temporarily unavailable</h1>
          <p className="text-sm text-gray-500">We couldn't reach our services. Please try again shortly, or contact us directly if this persists.</p>
        </div>
      </div>
    );
  }

  const navLinks = [
    { href: `/`,          label: 'Home' },
    { href: `/services`, label: 'Services' },
    { href: `/about`,    label: 'About Us' },
    { href: `/contact`,  label: 'Contact' },
    { href: `/track`,    label: 'Track Application' },
  ];

  const isActive = (href: string) => pathname === href;
  const primary  = tenant.primaryColor;
  const accent   = tenant.accentColor;

  return (
    <TenantCtx.Provider value={tenant}>
      <OfflineProvider authHeader={() => ({})} />
      <ToastContainer />
      <div className="min-h-screen bg-white font-sans">

        {/* Top utility bar */}
        <div style={{ backgroundColor: '#0B1220' }} className="text-white/80 text-xs py-2 px-4">
          <div className="max-w-7xl mx-auto flex items-center justify-between">
            <div className="flex items-center gap-6">
              {tenant.contactPhone && <span className="flex items-center gap-1.5"><IconPhone /> {tenant.contactPhone}</span>}
              {tenant.contactEmail && <span className="hidden sm:flex items-center gap-1.5"><IconMail /> {tenant.contactEmail}</span>}
            </div>
            <div className="flex items-center gap-1.5 text-white/60">
              <IconShield /> <span className="hidden sm:inline">Licensed &amp; regulated financial institution</span>
              <span className="sm:hidden">Regulated institution</span>
            </div>
          </div>
        </div>

        {/* Main nav */}
        <nav className="bg-white border-b border-gray-200 sticky top-0 z-50">
          <div className="max-w-7xl mx-auto px-4 py-3.5 flex items-center justify-between">
            {/* Brand */}
            <Link href={`/`} className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-md flex items-center justify-center text-white font-bold text-lg"
                style={{ backgroundColor: primary }}>
                {tenant.name[0]}
              </div>
              <div>
                <div className="font-bold text-gray-900 text-lg leading-tight tracking-tight">{tenant.name}</div>
                <div className="text-[11px] font-semibold uppercase tracking-wider" style={{ color: primary }}>{tenant.tagline}</div>
              </div>
            </Link>

            {/* Desktop nav */}
            <div className="hidden md:flex items-center gap-1">
              {navLinks.map(link => (
                <Link key={link.href} href={link.href}
                  className={`px-4 py-2 rounded-md text-sm font-semibold transition-colors border-b-2
                    ${isActive(link.href) ? '' : 'text-gray-600 hover:text-gray-900 border-transparent'}`}
                  style={isActive(link.href) ? { color: primary, borderColor: primary } : {}}>
                  {link.label}
                </Link>
              ))}
              <Link href="/login" className="ml-2 px-4 py-2 rounded-md text-sm font-semibold border transition-colors hover:bg-gray-50"
                style={{ borderColor: '#D1D5DB', color: '#374151' }}>
                Staff Login
              </Link>
              <Link href="/apply" className="ml-1 px-5 py-2.5 rounded-md text-sm font-bold text-white shadow-sm hover:opacity-90 transition-opacity"
                style={{ backgroundColor: primary }}>
                Apply Now
              </Link>
            </div>

            {/* Mobile menu */}
            <button className="md:hidden" onClick={() => setMenuOpen(!menuOpen)} aria-label="Toggle menu">
              <div className="w-6 h-0.5 bg-gray-700 my-1.5" />
              <div className="w-6 h-0.5 bg-gray-700 my-1.5" />
              <div className="w-6 h-0.5 bg-gray-700 my-1.5" />
            </button>
          </div>

          {menuOpen && (
            <div className="md:hidden border-t border-gray-100 px-4 py-3 space-y-1">
              {navLinks.map(link => (
                <Link key={link.href} href={link.href} onClick={() => setMenuOpen(false)}
                  className="block px-4 py-2.5 rounded-md text-sm font-semibold text-gray-700 hover:bg-gray-50">
                  {link.label}
                </Link>
              ))}
              <Link href="/apply" onClick={() => setMenuOpen(false)}
                className="block px-4 py-2.5 rounded-md text-sm font-bold text-white text-center mt-2" style={{ backgroundColor: primary }}>
                Apply Now
              </Link>
              <Link href="/login" onClick={() => setMenuOpen(false)} className="block px-4 py-2.5 text-sm font-semibold" style={{ color: primary }}>
                Staff Login →
              </Link>
            </div>
          )}
        </nav>

        {/* Page content */}
        <main>{children}</main>

        {/* Footer */}
        <footer style={{ backgroundColor: '#0B1220' }} className="text-white mt-16">
          <div className="max-w-7xl mx-auto px-4 py-12 grid grid-cols-1 md:grid-cols-4 gap-8">
            <div className="md:col-span-2">
              <div className="text-xl font-bold mb-2">{tenant.name}</div>
              <div className="text-white/60 text-sm leading-relaxed mb-4 max-w-md">{tenant.mission}</div>
              <div className="text-sm text-white/50 space-y-1">
                {tenant.address && <div className="flex items-center gap-2">{tenant.address}</div>}
                {tenant.contactPhone && <div className="flex items-center gap-2"><IconPhone /> {tenant.contactPhone}</div>}
                {tenant.contactEmail && <div className="flex items-center gap-2"><IconMail /> {tenant.contactEmail}</div>}
              </div>
              {(tenant.socialMedia?.facebook || tenant.socialMedia?.instagram || tenant.socialMedia?.linkedin
                || tenant.socialMedia?.twitter || tenant.socialMedia?.whatsapp) && (
                <div className="flex items-center gap-3 mt-5">
                  {tenant.socialMedia?.facebook && (
                    <a href={tenant.socialMedia.facebook} target="_blank" rel="noopener noreferrer" aria-label="Facebook"
                      className="w-8 h-8 rounded-full bg-white/10 hover:bg-white/20 flex items-center justify-center transition-colors">
                      <IconFacebook />
                    </a>
                  )}
                  {tenant.socialMedia?.instagram && (
                    <a href={tenant.socialMedia.instagram} target="_blank" rel="noopener noreferrer" aria-label="Instagram"
                      className="w-8 h-8 rounded-full bg-white/10 hover:bg-white/20 flex items-center justify-center transition-colors">
                      <IconInstagram />
                    </a>
                  )}
                  {tenant.socialMedia?.linkedin && (
                    <a href={tenant.socialMedia.linkedin} target="_blank" rel="noopener noreferrer" aria-label="LinkedIn"
                      className="w-8 h-8 rounded-full bg-white/10 hover:bg-white/20 flex items-center justify-center transition-colors">
                      <IconLinkedin />
                    </a>
                  )}
                  {tenant.socialMedia?.twitter && (
                    <a href={tenant.socialMedia.twitter} target="_blank" rel="noopener noreferrer" aria-label="Twitter / X"
                      className="w-8 h-8 rounded-full bg-white/10 hover:bg-white/20 flex items-center justify-center transition-colors">
                      <IconTwitter />
                    </a>
                  )}
                  {tenant.socialMedia?.whatsapp && (
                    <a href={tenant.socialMedia.whatsapp} target="_blank" rel="noopener noreferrer" aria-label="WhatsApp"
                      className="w-8 h-8 rounded-full bg-white/10 hover:bg-white/20 flex items-center justify-center transition-colors">
                      <IconWhatsapp />
                    </a>
                  )}
                </div>
              )}
            </div>
            <div>
              <div className="font-semibold mb-4 text-white/90 text-sm uppercase tracking-wider">Quick Links</div>
              <div className="space-y-2 text-sm text-white/60">
                {navLinks.map(l => (
                  <Link key={l.href} href={l.href} className="block hover:text-white transition">{l.label}</Link>
                ))}
              </div>
            </div>
            <div>
              <div className="font-semibold mb-4 text-white/90 text-sm uppercase tracking-wider">Our Services</div>
              <div className="space-y-2 text-sm text-white/60">
                {tenant.services?.slice(0,5).map(s => (
                  <div key={s.title}>{s.title}</div>
                ))}
              </div>
            </div>
          </div>
          <div className="border-t border-white/10 px-4 py-4">
            <div className="max-w-7xl mx-auto flex flex-col md:flex-row items-center justify-between text-xs text-white/40 gap-2">
              <span>© {new Date().getFullYear()} {tenant.name}. All rights reserved. {tenant.registrationNumber ? `Reg. No. ${tenant.registrationNumber}` : ''}</span>
              <span className="flex items-center gap-4">
                <Link href="/terms" className="hover:text-white/70 transition">Terms &amp; Conditions</Link>
                <Link href="/privacy" className="hover:text-white/70 transition">Privacy Policy</Link>
              </span>
              <span>Your deposits and data are protected in line with applicable financial regulations.</span>
            </div>
          </div>
        </footer>
      </div>
    </TenantCtx.Provider>
  );
}