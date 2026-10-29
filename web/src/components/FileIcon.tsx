import {
  File,
  FileArchive,
  FileAudio,
  FileCode,
  FileImage,
  FileSpreadsheet,
  FileText,
  FileVideo,
  Presentation,
} from "lucide-react";

function ext(name: string) {
  const i = name.lastIndexOf(".");
  return i > 0 ? name.slice(i + 1).toLowerCase() : "";
}

export function fileExt(name: string) {
  return ext(name).toUpperCase();
}

export function FileIcon({ mime, name, size = 22 }: { mime: string; name: string; size?: number }) {
  const e = ext(name);
  const m = mime || "";
  if (m.startsWith("image/")) return <FileImage size={size} />;
  if (m.startsWith("video/")) return <FileVideo size={size} />;
  if (m.startsWith("audio/")) return <FileAudio size={size} />;
  if (/zip|rar|7z|tar|gzip|compressed/.test(m) || ["zip", "rar", "7z", "tar", "gz", "tgz"].includes(e))
    return <FileArchive size={size} />;
  if (/sheet|excel|csv/.test(m) || ["xls", "xlsx", "csv", "ods"].includes(e)) return <FileSpreadsheet size={size} />;
  if (/presentation|powerpoint/.test(m) || ["ppt", "pptx", "odp", "key"].includes(e)) return <Presentation size={size} />;
  if (/json|javascript|xml|html|x-sh/.test(m) || ["js", "ts", "json", "go", "py", "kt", "java", "html", "css", "sh"].includes(e))
    return <FileCode size={size} />;
  if (m.startsWith("text/") || /pdf|word|document|rtf/.test(m) || ["pdf", "doc", "docx", "txt", "md", "rtf", "odt"].includes(e))
    return <FileText size={size} />;
  return <File size={size} />;
}
