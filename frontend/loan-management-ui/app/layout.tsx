import type { Metadata } from 'next';
import './globals.css';

// Perfect, edge-to-edge square data block embedding your exact GFS logo layout
const GFS_PRODUCTIONS_ICON = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNDAgMjQwIiB3aWR0aD0iMTAwJSIgaGVpZ2h0PSIxMDAlIj48cmVjdCB3aWR0aD0iMjQwIiBoZWlnaHQ9IjI0MCIgZmlsbD0iI0ZGRkZGRiIgcng9IjI0Ii8+PGcgdHJhbnNmb3JtPSJ0cmFuc2xhdGUoMTUsIDYwKSBzY2FsZSgwLjUzKSI+PHBhdGggZD0iTSAxNjAgNzAgTCAxNDAgNzAgTCAxNDAgNTAgTCAxNjAgNTAgTCAxNjAgMzAgTCAxMTAgMzAgQyA3MSAzMCwgNTAgNTAsIDUwIDkwIEwgNTAgMTUwIEMgNTAgMTkwLCA3MSAyMTAsIDExMCAyMTAgTCAxNjAgMjEwIEMgMTk5IDIxMCwgMjIwIDE5MCwgMjIwIDE1MCBMIDIyMCAxMTAgTCAxNDAgMTEwIEwgMTQwIDEzMCBMIDE5OCAxMzAgTCAxOTggMTUwIEMgMTk4IDE3NCwgMTg2IDE5MCwgMTYwIDE5MCBMIDExMCAxOTAgQyA4NCAxOTAsIDcyIDE3NCwgNzIgMTUwIEwgNzIgOTAgQyA3MiA2NiwgODQgNTAsIDExMCA1MCBMIDE0MCA1MCBMIDE0MCA3MCBaIiBmaWxsPSIjM0UzQTQ3Ii8+PHBhdGggZD0iTSAyNDAgMzAgTCA0MjAgMzAgTCA0MjAgOTAgTCAzOTYgOTAgTCAzOTYgNTIgTCAzMTYgNTIgTCAzMTYgMTA0IEwgMzYwIDEwNCBMIDM2MCAxMjYgTCAzMTYgMTI2IEwgMzE2IDIxMCBMIDI0MCAyMTAgTCAyNDAgMzAgWiIgZmlsbD0iIzJDQjQ0QiIvPjxwYXRoIGQ9Ik0gMzYwIDkwIEwgMzYwIDcwIEwgNDIwIDcwIEwgNDIwIDkwIFogTSAyOTggMTEwIEwgMjk4IDEzMCBMIDM3MCAxMzAgQyAzOTYgMTMwLCA0MDggMTQ2LCA0MDggMTcwIEwgNDA4IDE5MCBMIDM0MCAxOTAgTCAzNDAgMTY4IEwgMzg2IDE2OCBMIDM4NiAxNTAgTCAzMTQgMTUwIEMgMjg4IDE1MCwgMjc2IDEzNCwgMjc2IDExMCBMIDI3NiA5MCBMIDM0MCA5MCBMIDM0MCAxMTAgWiIgZmlsbD0iIzNFM0E0NyIvPjwvZz48L3N2Zz4=";

export const metadata: Metadata = {
  title: 'Growth Finance Services Ltd — Loans & Financial Services',
  description: 'Growth Finance Services Ltd — licensed loan products, online applications, and secure account management for individuals, businesses, and farmers in Rwanda.',
  icons: {
    icon: GFS_PRODUCTIONS_ICON,
    shortcut: GFS_PRODUCTIONS_ICON,
    apple: GFS_PRODUCTIONS_ICON,
  },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <head>
        <link rel="preconnect" href="https://googleapis.com" />
        <link rel="preconnect" href="https://gstatic.com" crossOrigin="anonymous" />
        <link
          href="https://googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&family=Sora:wght@400;600;700;800&display=swap"
          rel="stylesheet"
        />
      </head>
      <body className="bg-gray-50 text-gray-900 antialiased" style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
        {children}
      </body>
    </html>
  );
}