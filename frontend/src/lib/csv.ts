'use client';

/**
 * 前端 CSV 导出工具（零依赖，Excel 兼容）
 * - 带 UTF-8 BOM，Excel 直接打开不乱码
 * - 逗号/引号/换行按 RFC 4180 转义
 */
export function downloadCsv(filename: string, headers: string[], rows: (string | number | null | undefined)[][]) {
  const escapeCell = (v: string | number | null | undefined) => {
    const s = v == null ? '' : String(v);
    // RFC 4180：含逗号/引号/换行的字段用引号包裹并双写内部引号
    return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const lines = [headers.map(escapeCell).join(',')];
  for (const row of rows) {
    lines.push(row.map(escapeCell).join(','));
  }
  // \uFEFF = UTF-8 BOM，Excel 识别中文必需
  const csv = '\uFEFF' + lines.join('\r\n');

  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename.endsWith('.csv') ? filename : filename + '.csv';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
