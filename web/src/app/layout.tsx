import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Nokat Chat",
  description: "Nokat Chat — çalt we ygtybarly habarlaşyk",
  icons: { icon: "/logo.svg", apple: "/logo.svg" },
  applicationName: "Nokat Chat",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#2563EB" },
    { media: "(prefers-color-scheme: dark)", color: "#0B1220" },
  ],
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="tk">
      <body className="font-sans">{children}</body>
    </html>
  );
}
