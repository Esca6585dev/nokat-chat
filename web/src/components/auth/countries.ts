export interface Country {
  iso: string;
  flag: string;
  code: string; // dial code incl. "+"
  name: string;
  placeholder: string;
}

export const COUNTRIES: Country[] = [
  { iso: "TM", flag: "🇹🇲", code: "+993", name: "Türkmenistan", placeholder: "65 123456" },
  { iso: "RU", flag: "🇷🇺", code: "+7", name: "Russiýa", placeholder: "912 345 67 89" },
  { iso: "KZ", flag: "🇰🇿", code: "+7", name: "Gazagystan", placeholder: "701 234 56 78" },
  { iso: "UZ", flag: "🇺🇿", code: "+998", name: "Özbegistan", placeholder: "90 123 45 67" },
  { iso: "TR", flag: "🇹🇷", code: "+90", name: "Türkiýe", placeholder: "501 234 56 78" },
  { iso: "AZ", flag: "🇦🇿", code: "+994", name: "Azerbaýjan", placeholder: "50 123 45 67" },
  { iso: "IR", flag: "🇮🇷", code: "+98", name: "Eýran", placeholder: "912 345 6789" },
  { iso: "AE", flag: "🇦🇪", code: "+971", name: "BAE", placeholder: "50 123 4567" },
  { iso: "CN", flag: "🇨🇳", code: "+86", name: "Hytaý", placeholder: "131 2345 6789" },
  { iso: "DE", flag: "🇩🇪", code: "+49", name: "Germaniýa", placeholder: "1512 3456789" },
  { iso: "GB", flag: "🇬🇧", code: "+44", name: "Beýik Britaniýa", placeholder: "7400 123456" },
  { iso: "US", flag: "🇺🇸", code: "+1", name: "ABŞ", placeholder: "201 555 0123" },
];
