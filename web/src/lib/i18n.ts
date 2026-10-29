/**
 * All user-facing strings (Turkmen). Keep every UI string here so the app can be
 * translated by swapping this object.
 */
export const t = {
  appName: "Nokat Chat",
  tagline: "Ýakynlaryňyz bilen çalt we ygtybarly habarlaşyň",

  // Login
  loginTitle: "Telefon belgiňiz",
  loginSubtitle: "Ýurduň kodyny saýlaň we telefon belgiňizi giriziň.",
  phonePlaceholder: "65 123456",
  countryCode: "Ýurt kody",
  sendCode: "Kod iber",
  invalidPhone: "Telefon belgisi nädogry",
  otpTitle: "Tassyklaýyş kody",
  otpSubtitle: (phone: string) => `${phone} belgä 6 sanly kod iberildi.`,
  verify: "Tassykla",
  resendIn: (s: number) => `Täzeden ibermek: ${s} sek`,
  resend: "Kody täzeden iber",
  changeNumber: "Belgini üýtget",
  devCode: (code: string) => `Test kody: ${code}`,
  invalidCode: "Kod nädogry",
  tooManyRequests: "Gaty köp synanyşyk. Biraz garaşyň.",
  networkError: "Serwer bilen baglanyşyk ýok",
  somethingWrong: "Bir ýalňyşlyk ýüze çykdy",
  featureRealtime: "Real wagtda habarlar",
  featureChats: "Şahsy we topar söhbetdeşlikleri",
  featureSecure: "Telefon belgisi bilen howpsuz giriş",
  termsHint: "Dowam etmek bilen siz hyzmat şertlerini kabul edýärsiňiz.",

  // Profile setup
  setupTitle: "Profiliňizi dörediň",
  setupSubtitle: "Adyňyzy we ulanyjy adyňyzy giriziň — dostlaryňyz sizi tapar ýaly.",
  name: "Adyňyz",
  namePlaceholder: "Mysal üçin: Aman",
  username: "Ulanyjy ady",
  usernamePlaceholder: "aman",
  usernameHint: "3–32 harp: a–z, 0–9 we _",
  usernameInvalid: "Ulanyjy ady diňe a–z, 0–9 we _ bolup biler (3–32)",
  usernameTaken: "Bu ulanyjy ady eýýäm alnan",
  bio: "Özüňiz barada",
  bioPlaceholder: "Gysgaça maglumat",
  nameRequired: "Adyňyzy giriziň",
  continue: "Dowam et",
  save: "Ýatda sakla",
  saved: "Ýatda saklandy",

  // Chat list
  chats: "Söhbetdeşlikler",
  search: "Gözle",
  noChats: "Heniz söhbetdeşlik ýok",
  noChatsHint: "Täze söhbetdeşlige başlamak üçin aşakdaky düwmä basyň.",
  noResults: "Hiç zat tapylmady",
  globalSearch: "Ulanyjylar",
  yourChats: "Söhbetdeşlikler",
  newChat: "Täze söhbetdeşlik",
  newGroup: "Täze topar",
  you: "Siz",
  draftEmpty: "Habar ýok",

  // Conversation
  selectChat: "Habarlaşmak üçin söhbetdeşlik saýlaň",
  selectChatHint: "Ýa-da täze söhbetdeşlige başlaň",
  messagePlaceholder: "Hat ýaz...",
  send: "Ugrat",
  online: "onlaýn",
  offline: "onlaýn däl",
  typing: "ýazýar…",
  typingUser: (name: string) => `${name} ýazýar…`,
  typingMany: (n: number) => `${n} adam ýazýar…`,
  lastSeenJustNow: "jaňy onlaýn bolupdy",
  lastSeen: (when: string) => `soňky gezek: ${when}`,
  today: "Şu gün",
  yesterday: "Düýn",
  members: (n: number) => `${n} agza`,
  membersOnline: (n: number, o: number) => `${n} agza, ${o} onlaýn`,
  loadingOlder: "Ýüklenýär…",
  sending: "Iberilýär",
  failed: "Iberilmedi. Gaýtadan synanyşmak üçin basyň",
  retry: "Gaýtala",
  back: "Yza",
  scrollDown: "Aşak",
  startConversation: "Söhbetdeşligi başlaň — ilkinji habary ugradyň 👋",
  connecting: "Birikdirilýär…",

  // New chat / group
  searchUsers: "At, ulanyjy ady ýa-da telefon",
  searchHint: "Ulanyjy tapmak üçin ýazmaga başlaň",
  groupTitle: "Toparyň ady",
  groupTitlePlaceholder: "Mysal üçin: Maşgala",
  addMembers: "Agzalary goş",
  selected: (n: number) => `${n} saýlandy`,
  create: "Döret",
  cancel: "Ýatyr",
  close: "Ýap",

  // Settings
  settings: "Sazlamalar",
  profile: "Profil",
  phone: "Telefon",
  logout: "Çykmak",
  logoutConfirm: "Hakykatdan hem çykmak isleýärsiňizmi?",
  darkModeHint: "Garaňky tema enjamyňyzyň sazlamasyna görä açylýar.",
  version: "Wersiýa",
};

export const months = [
  "ýanwar",
  "fewral",
  "mart",
  "aprel",
  "maý",
  "iýun",
  "iýul",
  "awgust",
  "sentýabr",
  "oktýabr",
  "noýabr",
  "dekabr",
];

/** Index 0 = Sunday (Date.getDay()). */
export const weekdaysShort = ["Ýek", "Duş", "Siş", "Çar", "Pen", "Ann", "Şen"];
