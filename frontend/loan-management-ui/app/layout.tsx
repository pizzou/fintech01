import type { Metadata } from 'next';
import './globals.css';

const FAVICON_DATA_URI = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAEuElEQVR42u1YQWsbRxR+q5FsyWosakMreuq111xCDykIZLCKEVGoTdhdQaqN/4R/QPojHIUGSUshLRuMiQwqqNinXnzt1Ue54Ji1KkvW7ooerCdGk9ldrTx2Ap4Hi0Y7s2/efPPme2+eoihwryUGIAGQAEgAJAASAAmABEACIAGQAEgAJAASAAnAfZM4+0LXjKwo5fVGtcO+e/znD/FPueCj/KE79UJRrp+ybmSxLeq5DZ3iH2V693v2cE0E0unMQov1gNJbNWNtmvb6wfNnn8oDUt1h09o07YkDlHUjW29UO6WNcnkwuNoVOVkyubht7ddq7PtH759ehZLTUjyB7dGl6whF4VLR/v7pj9+vT4ACcBuL9wPh+7+2RsIXNId800t+ZW2adgwAgLdLosTzvJyQcEV5hAjBY+DLyIlE3CSEtKMqZj3JcVwVACpB7jiL3kxsKc4G7fP/ui5v7JdfPOCu63x08SY0DKIsr6R2eGEsTIoFNTde9ATIoPF4Fu9CHr1/OjsA42hQ07VrkgxT/uLhduLV8a5DCGmj56QzCy2M/R/FX050KL1VM2Euy5Og3KX3pN9Pv0ulek/6fetHc3H94Pmzg/VffwsFIKq8Ot51/Pjk8c/zJz/pd6kURimaT2gPDdygBgAA2ONfoBcvFAARhOSzyzaSKX20evawrWtG66YZamQAdM3IXnzov5x1PCGkbeX9o0xQUpTqDpurv6Qv0btYsh0MruYCvFhQzb2mWdE1IxuPsnBEjt6JMBmP9QWAx8wY9s5HrmMdm4sIZJR5w2wqFlSoN6qVz/Y2yCZL1n6tFhZRooKga0b2swEgthRP4OOX+CyvpHZEghCJAxjGTfJCT71R7RQL6usgV/0oDR4nQqNLj76jjv8rPBsqgYlVgBTyW4O5AZjlrj+P3FUipGtG9uy0C0IAePFw2zcv/xd6kfUFFUmO8ocuelvPHq5hLrDXNCtBdgAADL4brQIAJP+JnQ1gFO0IYBbH22leWJpkdRvlyGztlyXqmpE9gsMOAMDZafeE6a4E2QEAAMfQoduF/NbsAFx86L8sbZQjX4ZE3f7CpLRRLke1y3Hc2QFwHFcVFXdZthdRDxBVv4jNg2bUgshNiVYU4bLX/Xqj2onheRcdX3GSdGahxSO5WQscSICrXz/4VqRdyyupnUlJjJ5MdFE06DqN1+AooUyEbVP23FX5OaxETvf7tW/HHmX6lkcIafNK2mE70rOHa1gL4HnSTeuOtM4w+5DTZplTUZRJlnSChJXOLLToyXr2cA3zAjZU4k1tMLjaxQowq4+XX7D60WA0np6XTrGTycVtnn2sbrbNW0+9Ue1MyuKe5+X2mmalWFBf0wvyPC9HCGkHxXe6H8nl7LR7goVV3rdBOuk+x3FVJEAWcJq4g+bBsInjEUhrv1abRAHHcVVE3/O8HDJ4UH6Ai6Unol2f7uf9Z7/1Y2zUizUBdqGEkLa1X6uhXUH6CCHtRCJuoo4pDmBdEd3Z7wiwrsy+x++jfsdzVZ59vO9584QegfssMT9mZ3/pft47dizb9hvDm89PD2+cn46gueg+6QEAEgAJgARAAiABkABIACQAEgAJgARAAiABkADcN/kfHO0DIf1f+8wAAAAASUVORK5CYII=";

export const metadata: Metadata = {
  title: 'Growth Finance Services Ltd — Loans & Financial Services',
  description: 'Growth Finance Services Ltd — licensed loan products, online applications, and secure account management for individuals, businesses, and farmers in Rwanda.',
  icons: {
    icon: FAVICON_DATA_URI,
    shortcut: FAVICON_DATA_URI,
    apple: FAVICON_DATA_URI,
  },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&family=Sora:wght@400;600;700;800&display=swap"
          rel="stylesheet"
        />
      </head>
      <body className="bg-gray-50 text-gray-900 antialiased" style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
        {children}
      </body>
    </html>
  );
}