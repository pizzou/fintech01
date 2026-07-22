import type { Metadata } from 'next';
import './globals.css';

// Exact GFS logo embedded on a bright white shield to force 100% visibility on all dark and light browser tabs
const GFS_HIGH_CONTRAST_ICON = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNTAgMjUwIiB3aWR0aD0iMTAwJSIgaGVpZ2h0PSIxMDAlIj48cmVjdCB3aWR0aD0iMjUwIiBoZWlnaHQ9IjI0MCIgcng9IjU1IiBmaWxsPSIjRkZGRkZGIi8+PGcgdHJhbnNmb3JtPSJ0cmFuc2xhdGUoMjAsIDU1KSBzY2FsZSgwLjUpIj48cGF0aCBkPSJNIDE2MCA3MCBMIDE0MCA3MCBMIDE0MCA1MCBMIDE2MCA1MCBMIDE2MCAzMCBMIDExMCAzMCBDIDcxIDMwLCA1MCA1MCwgNTAgOTAgTCA1MCAxNTAgQyA1MCAxOTAsIDcxIDIxMCwgMTEwIDIxMCBMIDE2MCAyMTAgQyAxOTkgMjEwLCAyMjAgMTkwLCAyMjAgMTUwIEwgMjIwIDExMCBMIDE0MCAxMTAgTCAxNDAgMTMwIEwgIKU4IDEzMCBMIDE5OCAxNTAgQyAxOTggMTc0LCAxODYgMTkwLCAxNjAgMTkwIEwgMExMCAxOTAgQyA4NCAxOTAsIDcyIDE3NCwgNzIgMTUwIEwgNzIgOTAgQyA3MiA2NiwgODQgNTAsIDExMCA1MCBMIDE0MCA1MCBMIDE0MCA3MCBaIiBmaWxsPSIjM0UzQTQ3Ii8+PHBhdGggZD0iTSAyNDAgMzAgTCA0MjAgMzAgTCA0MjAgOTAgTCAzOTYgOTAgTCAzOTYgNTIgTCAzMTYgNTIgTCAzMTYgMTA0IEwgMzYwIDEwNCBMIDM2MCAxMjYgTCAzMTYgMTI2IEwgMzE2IDIxMCBMIDI0MCAyMTAgTCAyNDAgMzAgWiIgZmlsbD0iIzJDQjQ0QiIvPjxwYXRoIGQ9Ik0gMzYwIDkwIEwgMzYwIDcwIEwgNDIwIDcwIEwgNDIwIDkwIFogTSAyOTggMTEwIEwgMjk4IDEzMCBMIDM3MCAxMzAgQyAzOTYgMTMwLCAwMDggMTQ2LCA0MDggMTcwIEwgNDA4IDE5MCBMIDM0MCAxOTAgTCAzSubIDE2OCBMIDM4NiAxNjggTCAzODYgMTUwIEwgMzE0IDE1MCBDIDI4OCAxNTAsIDI3NiAxMzQsIDI3NiAxMTAgTCAyNzYgOTAgTCAzNDAgOTAgTCAzNDAgMTEwIFoiZmlsbD0iIzNFM0E0NyIvPjwvZz48L3N2Zz4=";

export const metadata: Metadata = {
  title: 'Growth Finance Services Ltd — Loans & Financial Services',
  description: 'Growth Finance Services Ltd — licensed loan products, online applications, and secure account management for individuals, businesses, and farmers in Rwanda.',
  icons: {
    icon: GFS_HIGH_CONTRAST_ICON,
    shortcut: GFS_HIGH_CONTRAST_ICON,
    apple: GFS_HIGH_CONTRAST_ICON,
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