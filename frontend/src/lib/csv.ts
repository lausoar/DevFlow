'use client';

export type CsvCell = string | number | null | undefined;

// Excel 等表格软件会把这些前缀解释为公式；制表符和换行也可能被用来隐藏公式前缀。
const DANGEROUS_STRING_PREFIX = /^[=+\-@\t\r\n]/;
const ORDINARY_NEGATIVE_NUMBER = /^-(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/;

/** 在 RFC 4180 转义之前中和公式前缀，同时保留普通负数文本。 */
export function escapeCsvCell(value: CsvCell): string {
  const original = value == null ? '' : String(value);
  const shouldNeutralize = typeof value === 'string'
    && DANGEROUS_STRING_PREFIX.test(original)
    && !ORDINARY_NEGATIVE_NUMBER.test(original);
  const safe = shouldNeutralize ? `'${original}` : original;
  // RFC 4180：含逗号/引号/换行的字段用引号包裹并双写内部引号
  return /[",\r\n]/.test(safe) ? `"${safe.replace(/"/g, '""')}"` : safe;
}

/** 生成带 UTF-8 BOM 的完整 CSV，供下载和直接单元测试共用。 */
export function serializeCsv(headers: string[], rows: CsvCell[][]): string {
  const lines = [headers.map(escapeCsvCell).join(',')];
  for (const row of rows) {
    lines.push(row.map(escapeCsvCell).join(','));
  }
  return String.fromCharCode(0xfeff) + lines.join('\r\n');
}

/**
 * 前端 CSV 导出工具（零依赖，Excel 兼容）
 * - 带 UTF-8 BOM，Excel 直接打开不乱码
 * - 公式前缀在逗号/引号/换行的 RFC 4180 转义之前中和
 */
export function downloadCsv(filename: string, headers: string[], rows: CsvCell[][]) {
  const csv = serializeCsv(headers, rows);
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
